package com.jinroo.agent

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import java.nio.FloatBuffer

data class Action(val x: Int, val y: Int)

/** Contract: float32 NCHW RGB [1,3,224,224], scaled 0..1; output float32 [1,3]
 * containing normalized x, y and confidence. A separately trained model is required. */
class InferenceEngine(context: Context) : AutoCloseable {
    private val env = OrtEnvironment.getEnvironment()
    private val session: OrtSession
    init {
        val bytes = context.assets.open("model.onnx").use { it.readBytes() }
        val opts = OrtSession.SessionOptions()
        session = try { env.createSession(bytes, opts) } finally { opts.close() }
        require(session.inputNames.size == 1 && session.outputNames.size == 1) { "El modelo debe tener una entrada y una salida" }
    }
    fun infer(bitmap: Bitmap): Action? {
        val small = Bitmap.createScaledBitmap(bitmap, 224, 224, true)
        try {
            val pixels = IntArray(224 * 224)
            small.getPixels(pixels, 0, 224, 0, 0, 224, 224)
            val input = FloatArray(3 * pixels.size)
            for (i in pixels.indices) {
                input[i] = ((pixels[i] shr 16) and 255) / 255f
                input[pixels.size + i] = ((pixels[i] shr 8) and 255) / 255f
                input[2 * pixels.size + i] = (pixels[i] and 255) / 255f
            }
            OnnxTensor.createTensor(env, FloatBuffer.wrap(input), longArrayOf(1, 3, 224, 224)).use { tensor ->
                session.run(mapOf(session.inputNames.first() to tensor)).use { result ->
                    val output = (result[0].value as? Array<*>)?.firstOrNull() as? FloatArray
                        ?: error("La salida debe ser float32 [1,3]")
                    return ActionDecoder.decode(output, bitmap.width, bitmap.height)
                }
            }
        } finally { if (small !== bitmap) small.recycle() }
    }
    override fun close() { session.close() }
}
