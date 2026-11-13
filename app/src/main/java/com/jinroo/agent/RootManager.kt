package com.jinroo.agent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

object RootManager {
    init { System.loadLibrary("jinroo") }
    external fun nativeIsProcessRoot(): Boolean

    // Fixed, internally generated commands only. Never pass arbitrary UI text here.
    private fun execute(command: String): Boolean {
        var process: Process? = null
        return try {
            process = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
            if (!process.waitFor(15, TimeUnit.SECONDS)) { process.destroyForcibly(); false }
            else process.exitValue() == 0
        } catch (_: Exception) { false } finally { process?.destroy() }
    }
    suspend fun check(): Boolean = withContext(Dispatchers.IO) { execute("id -u | grep -x 0") }
    suspend fun tap(x: Int, y: Int): Boolean = withContext(Dispatchers.IO) {
        execute("input tap ${x.coerceAtLeast(0)} ${y.coerceAtLeast(0)}")
    }
}
