package com.jinroo.agent

import org.junit.Assert.*
import org.junit.Test

class ActionDecoderTest {
    @Test fun mapsCornersAndCenter() {
        assertEquals(Action(0, 0), ActionDecoder.decode(floatArrayOf(0f, 0f, 1f), 101, 201))
        assertEquals(Action(100, 200), ActionDecoder.decode(floatArrayOf(1f, 1f, 0.8f), 101, 201))
        assertEquals(Action(50, 100), ActionDecoder.decode(floatArrayOf(0.5f, 0.5f, 0.9f), 101, 201))
    }
    @Test fun rejectsInvalidOrLowConfidenceOutputs() {
        for (v in listOf(floatArrayOf(-0.1f, 0f, 1f), floatArrayOf(0f, 1.1f, 1f),
            floatArrayOf(Float.NaN, 0f, 1f), floatArrayOf(0f, 0f, Float.POSITIVE_INFINITY),
            floatArrayOf(0f, 0f, 0.79f), floatArrayOf(0f, 0f, 1.01f))) {
            assertNull(ActionDecoder.decode(v, 100, 100))
        }
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsWrongShape() {
        ActionDecoder.decode(floatArrayOf(0f, 0f), 100, 100)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsInvalidDimensions() {
        ActionDecoder.decode(floatArrayOf(0f, 0f, 1f), 0, 100)
    }
}
