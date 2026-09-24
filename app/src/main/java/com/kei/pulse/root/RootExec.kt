package com.kei.pulse.root

import android.annotation.SuppressLint

/**
 * Magisk/su-backed implementation of root command execution.
 *
 * The upstream Pulse project talks to a custom binder service ("PServerBinder") that
 * AYN/Retroid ship in their firmware. The AYANEO Pocket S 2K (SG8275 / kalama) does not
 * expose that service — confirmed via `service list` on-device — so this fork replaces the
 * binder transaction with a standard Magisk `su` shell invocation instead.
 *
 * The public surface (`pServerAvailable`, `executeAsRoot`) is kept identical to the
 * upstream RootExec so RootCommandRunner and RootSupport require no changes.
 */
@SuppressLint("DiscouragedPrivateApi", "PrivateApi")
class RootExec {

    // Name kept for drop-in compatibility with RootCommandRunner/RootSupport, which read
    // this to decide whether privileged execution is available at all on this device.
    var pServerAvailable: Boolean = false
        private set

    init {
        pServerAvailable = probeSu()
    }

    private fun probeSu(): Boolean = runCatching {
        val process = ProcessBuilder("su", "-c", "id").redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        val exited = process.waitFor()
        exited == 0 && output.contains("uid=0")
    }.getOrDefault(false)

    fun executeAsRoot(cmd: String): Result<String?> {
        if (!pServerAvailable) return Result.failure(IllegalStateException("su not available"))

        return runCatching {
            val process = ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            if (output.isEmpty() || output == "null") null else output
        }
    }
}
