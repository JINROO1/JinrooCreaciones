package com.jinroo.agent

/** Decode only valid, confident normalized coordinates; never clamp malformed model outputs. */
object ActionDecoder {
    fun decode(output: FloatArray, width: Int, height: Int, threshold: Float = 0.8f): Action? {
        require(output.size == 3) { "La salida debe ser float32 [1,3]" }
        require(width > 0 && height > 0) { "Dimensiones de pantalla inválidas" }
        require(threshold.isFinite() && threshold in 0f..1f) { "Umbral inválido" }
        val (x, y, confidence) = output
        if (!x.isFinite() || !y.isFinite() || !confidence.isFinite() ||
            x !in 0f..1f || y !in 0f..1f || confidence !in threshold..1f) return null
        return Action((x * (width - 1)).toInt(), (y * (height - 1)).toInt())
    }
}
