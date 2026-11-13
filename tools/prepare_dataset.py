#!/usr/bin/env python3
"""Extract annotated samples from a locally supplied video; no network access."""
import argparse
import hashlib
import json
import math
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

ACTIONS = {"tap", "swipe", "hold", "wait", "touch_frame"}
MAX_CONTACTS = 10
MAX_SAMPLES = 100_000


def number(value, name, lo, hi):
    if isinstance(value, bool) or not isinstance(value, (float, int)) or not math.isfinite(value) or not lo <= value <= hi:
        raise ValueError(f"{name} debe ser un número finito entre {lo} y {hi}")
    return float(value)


def point(value, name):
    if not isinstance(value, list) or len(value) != 2:
        raise ValueError(f"{name} debe ser [x,y] normalizado")
    return [number(value[0], name + '.x', 0, 1), number(value[1], name + '.y', 0, 1)]


def validate_event(raw):
    if not isinstance(raw, dict):
        raise ValueError("cada línea debe contener un objeto JSON")
    action = raw.get('action')
    if not isinstance(action, str) or action not in ACTIONS:
        raise ValueError("acción desconocida; use tap, swipe, hold, wait o touch_frame")
    if action == 'touch_frame':
        if set(raw) != {'time_s', 'action', 'contacts'}:
            raise ValueError('touch_frame requiere únicamente time_s, action y contacts')
        contacts = raw['contacts']
        if not isinstance(contacts, list) or not 1 <= len(contacts) <= MAX_CONTACTS:
            raise ValueError(f'contacts debe contener entre 1 y {MAX_CONTACTS} entradas')
        parsed = []
        ids = set()
        for contact in contacts:
            if not isinstance(contact, dict) or set(contact) != {'id', 'state', 'position'}:
                raise ValueError('cada contacto requiere id, state y position')
            finger = contact['id']
            state = contact['state']
            if isinstance(finger, bool) or not isinstance(finger, int) or not 0 <= finger < MAX_CONTACTS:
                raise ValueError('id debe ser un entero entre 0 y 9')
            if finger in ids:
                raise ValueError('id duplicado en el mismo instante')
            ids.add(finger)
            if not isinstance(state, str) or state not in {'down', 'move', 'up'}:
                raise ValueError('state debe ser down, move o up')
            parsed.append({'id': finger, 'state': state, 'position': point(contact['position'], 'position')})
        return {'time_s': number(raw.get('time_s'), 'time_s', 0, 86400), 'action': action, 'contacts': parsed}
    allowed = {'time_s', 'action', 'duration_ms'}
    if action in {'tap', 'hold', 'swipe'}:
        allowed.add('start')
    if action == 'swipe':
        allowed.add('end')
    unknown = set(raw) - allowed
    if unknown:
        raise ValueError(f"campos inesperados: {sorted(unknown)}")
    event = {'time_s': number(raw.get('time_s'), 'time_s', 0, 86400), 'action': action}
    if action in {'tap', 'hold', 'swipe'}:
        event['start'] = point(raw.get('start'), 'start')
    if action == 'swipe':
        event['end'] = point(raw.get('end'), 'end')
    if action in {'hold', 'swipe', 'wait'}:
        duration = number(raw.get('duration_ms'), 'duration_ms', 1, 60000)
        event['duration_ms'] = duration
    elif 'duration_ms' in raw:
        raise ValueError('tap no acepta duration_ms')
    return event


def load_events(path):
    events = []
    active = set()
    is_touch_sequence = None
    with path.open(encoding='utf-8') as stream:
        for line_no, line in enumerate(stream, 1):
            if not line.strip():
                continue
            try:
                e = validate_event(json.loads(line))
                if events and e['time_s'] <= events[-1]['time_s']:
                    raise ValueError('los tiempos deben ser estrictamente crecientes')
                is_touch = e['action'] == 'touch_frame'
                if is_touch_sequence is None:
                    is_touch_sequence = is_touch
                elif is_touch_sequence != is_touch:
                    raise ValueError('no se pueden mezclar touch_frame y acciones simples')
                if is_touch:
                    for contact in e['contacts']:
                        finger, state = contact['id'], contact['state']
                        if state == 'down':
                            if finger in active:
                                raise ValueError(f'down duplicado para id {finger}')
                            active.add(finger)
                        else:
                            if finger not in active:
                                raise ValueError(f'{state} sin down previo para id {finger}')
                            if state == 'up':
                                active.remove(finger)
            except (ValueError, json.JSONDecodeError) as exc:
                raise ValueError(f'{path.name}, línea {line_no}: {exc}') from exc
            events.append(e)
            if len(events) > MAX_SAMPLES:
                raise ValueError('demasiados eventos')
    if not events:
        raise ValueError('no hay eventos')
    if active:
        raise ValueError(f'faltan eventos up para ids: {sorted(active)}')
    return events


def sha256(path):
    h = hashlib.sha256()
    with path.open('rb') as f:
        for block in iter(lambda: f.read(1024 * 1024), b''):
            h.update(block)
    return h.hexdigest()


def prepare(video, annotations, output, session, rights, ffmpeg='ffmpeg', split=None):
    video, annotations, output = Path(video), Path(annotations), Path(output)
    if not video.is_file() or not annotations.is_file():
        raise ValueError('el vídeo y las anotaciones deben ser archivos existentes')
    if not session.strip() or len(session) > 128 or not rights.strip() or len(rights) > 1000:
        raise ValueError('se requieren identificación de sesión y declaración de derechos no vacías')
    if split not in (None, 'train', 'val', 'test'):
        raise ValueError('split inválido')
    if output.exists():
        raise ValueError('el directorio de salida ya existe; no se sobrescribirá')
    events = load_events(annotations)
    digest = sha256(video)
    # All copies of the same video map to the same default split, regardless of session name.
    bucket = int(digest[:8], 16) % 100
    split = split or ('train' if bucket < 80 else 'val' if bucket < 90 else 'test')
    output.parent.mkdir(parents=True, exist_ok=True)
    temp = Path(tempfile.mkdtemp(prefix='.dataset-', dir=output.parent))
    try:
        (temp / 'frames').mkdir()
        with (temp / 'samples.jsonl').open('w', encoding='utf-8') as dest:
            for i, event in enumerate(events):
                rel = f'frames/{i:06d}.png'
                target = temp / rel
                cmd = [ffmpeg, '-nostdin', '-hide_banner', '-loglevel', 'error', '-ss', str(event['time_s']), '-i', str(video), '-frames:v', '1', '-y', str(target)]
                try:
                    proc = subprocess.run(cmd, stdin=subprocess.DEVNULL, capture_output=True, timeout=60, check=False)
                except (OSError, subprocess.TimeoutExpired) as exc:
                    raise ValueError(f'no se pudo ejecutar ffmpeg: {exc}') from exc
                if proc.returncode != 0 or not target.is_file() or target.stat().st_size == 0:
                    msg = proc.stderr.decode('utf-8', 'replace')[-1000:]
                    raise ValueError(f'falló la extracción en {event["time_s"]} s: {msg}')
                dest.write(json.dumps({'image': rel, 'event': event}, ensure_ascii=False, allow_nan=False) + '\n')
        meta = {'schema_version': 2 if events[0]['action'] == 'touch_frame' else 1, 'session': session, 'video_sha256': digest,
                'annotations_sha256': sha256(annotations), 'split': split, 'rights': rights,
                'sample_count': len(events), 'coordinate_system': 'normalized [0,1], origin top-left'}
        (temp / 'metadata.json').write_text(json.dumps(meta, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
        # Refuse to replace even an empty directory that appeared during extraction.
        if output.exists():
            raise ValueError('el directorio de salida apareció durante la extracción')
        os.rename(temp, output)
        return meta
    finally:
        if temp.exists():
            shutil.rmtree(temp)


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--video', type=Path, required=True)
    p.add_argument('--annotations', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    p.add_argument('--session', required=True)
    p.add_argument('--rights', required=True, help='Procedencia y autorización de uso del vídeo y las etiquetas')
    p.add_argument('--ffmpeg', default='ffmpeg')
    p.add_argument('--split', choices=['train', 'val', 'test'], help='Asigne por sesión/fuente para evitar filtración')
    args = p.parse_args()
    try:
        print(json.dumps(prepare(args.video, args.annotations, args.output, args.session, args.rights, args.ffmpeg, args.split), ensure_ascii=False, indent=2))
    except ValueError as exc:
        p.exit(2, f'Error: {exc}\n')


if __name__ == '__main__':
    main()
