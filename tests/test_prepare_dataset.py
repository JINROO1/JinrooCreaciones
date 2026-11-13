import json
import os
from pathlib import Path
import tempfile
import unittest
from tools.prepare_dataset import load_events, prepare, validate_event


class ValidationTests(unittest.TestCase):
    def test_events(self):
        self.assertEqual(validate_event({'time_s': 0, 'action': 'tap', 'start': [0.2, 1]})['start'], [0.2, 1.0])
        self.assertEqual(validate_event({'time_s': 3, 'action': 'swipe', 'start': [0, 0], 'end': [1, 1], 'duration_ms': 5})['duration_ms'], 5)
        self.assertEqual(validate_event({'time_s': 3, 'action': 'wait', 'duration_ms': 5})['action'], 'wait')

    def test_rejects_malformed_and_extra_fields(self):
        invalid = [
            {'time_s': True, 'action': 'wait', 'duration_ms': 1},
            {'time_s': float('nan'), 'action': 'wait', 'duration_ms': 1},
            {'time_s': 1, 'action': 'tap', 'start': [2, 0]},
            {'time_s': 1, 'action': 'tap', 'start': [0, 0], 'end': [1, 1]},
            {'time_s': 1, 'action': 'hold', 'start': [0, 0], 'duration_ms': 0},
            {'time_s': 1, 'action': [], 'duration_ms': 5},
        ]
        for event in invalid:
            with self.subTest(event=event), self.assertRaises(ValueError): validate_event(event)

    def test_strict_time_order(self):
        with tempfile.TemporaryDirectory() as d:
            p = Path(d) / 'events.jsonl'
            p.write_text('\n'.join([json.dumps({'time_s': 1, 'action': 'wait', 'duration_ms': 5})] * 2))
            with self.assertRaises(ValueError): load_events(p)

    def test_extraction_and_failure_cleanup(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d); video = root / 'v.mp4'; video.write_bytes(b'video')
            events = root / 'events.jsonl'; events.write_text('{"time_s":0,"action":"tap","start":[0.2,0.4]}\n')
            fake = root / 'ffmpeg'; fake.write_text('#!/bin/sh\nfor last; do :; done\nprintf frame > "$last"\n'); fake.chmod(0o755)
            out = root / 'output'
            meta = prepare(video, events, out, 'session-1', 'owner permission', str(fake))
            self.assertEqual(meta['sample_count'], 1)
            sample = json.loads((out / 'samples.jsonl').read_text())
            self.assertEqual((out / sample['image']).read_bytes(), b'frame')
            with self.assertRaises(ValueError): prepare(video, events, out, 'session', 'permission', str(fake))
            with self.assertRaises(ValueError): prepare(video, events, root / 'bad', 'session', 'permission', '/missing/ffmpeg')
            self.assertFalse((root / 'bad').exists())
            self.assertFalse(any(x.name.startswith('.dataset-') for x in root.iterdir()))

    def test_multitouch_lifecycle(self):
        def frame(t, *contacts):
            return {'time_s': t, 'action': 'touch_frame', 'contacts': [
                {'id': finger, 'state': state, 'position': [x, y]} for finger, state, x, y in contacts]}
        valid = [frame(0, (0, 'down', 0.1, 0.8)),
                 frame(0.1, (0, 'move', 0.2, 0.8), (1, 'down', 0.8, 0.6)),
                 frame(0.2, (0, 'up', 0.2, 0.8), (1, 'up', 0.8, 0.6))]
        with tempfile.TemporaryDirectory() as d:
            p = Path(d) / 'events.jsonl'
            def write(rows): p.write_text('\n'.join(json.dumps(e) for e in rows))
            write(valid)
            self.assertEqual(len(load_events(p)), 3)
            bad = [valid[:2], [frame(0, (0, 'move', 0, 0))],
                   [frame(0, (0, 'down', 0, 0)), frame(1, (0, 'down', 0, 0))],
                   [frame(0, (0, 'down', 0, 0), (0, 'up', 0, 0))],
                   [valid[0], {'time_s': 1, 'action': 'wait', 'duration_ms': 100}]]
            for rows in bad:
                with self.subTest(rows=rows), self.assertRaises(ValueError):
                    write(rows); load_events(p)

    def test_multitouch_output_schema(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d); video = root / 'video'; video.write_bytes(b'video')
            events = root / 'touches.jsonl'
            events.write_text('\n'.join(json.dumps({'time_s': t, 'action': 'touch_frame', 'contacts': [
                {'id': 0, 'state': state, 'position': [0.2, 0.8]}]}) for t, state in [(0, 'down'), (1, 'up')]))
            fake = root / 'ffmpeg'; fake.write_text('#!/bin/sh\nfor last; do :; done\nprintf frame > "$last"\n'); fake.chmod(0o755)
            meta = prepare(video, events, root / 'out', 'session', 'permission', str(fake))
            self.assertEqual(meta['schema_version'], 2)
            rows = (root / 'out' / 'samples.jsonl').read_text().splitlines()
            self.assertEqual(json.loads(rows[1])['event']['contacts'][0]['state'], 'up')

if __name__ == '__main__': unittest.main()
