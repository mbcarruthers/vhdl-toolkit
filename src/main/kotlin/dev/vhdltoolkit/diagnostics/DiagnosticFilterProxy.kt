@file:JvmName("DiagnosticFilterProxy")

package dev.vhdltoolkit.diagnostics

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import kotlin.system.exitProcess

/**
 * A transparent stdio proxy that sits between the IDE and VHDL-LS, filtering
 * `publishDiagnostics` on the way back.
 *
 * This exists because the decision "is this error a consequence of another?"
 * needs the whole diagnostic set for a file at once, which is exactly what one
 * `publishDiagnostics` notification carries. The IDE's own highlight filters
 * see diagnostics one at a time and cannot make that judgement.
 *
 * Usage: `DiagnosticFilterProxy <vhdl_ls path> [args...]`
 *
 * Everything other than publishDiagnostics is forwarded byte-for-byte, and any
 * failure in the filter falls through to the original message, so the worst
 * case is the unfiltered behaviour rather than a broken language server.
 */
fun main(args: Array<String>) {
    if (args.isEmpty()) {
        System.err.println("DiagnosticFilterProxy: expected the language server command")
        exitProcess(2)
    }

    val server = ProcessBuilder(args.toList())
        .redirectError(ProcessBuilder.Redirect.INHERIT)
        .start()

    // IDE -> server: straight copy, no inspection needed.
    val toServer = Thread({
        runCatching {
            System.`in`.copyTo(server.outputStream)
            server.outputStream.close()
        }
    }, "vhdl-ls-proxy-stdin").apply { isDaemon = true; start() }

    // server -> IDE: filtered.
    val serverOut = BufferedInputStream(server.inputStream)
    val ideOut = BufferedOutputStream(System.out)

    runCatching {
        while (true) {
            val message = LspFraming.readMessage(serverOut) ?: break
            val filtered = PublishDiagnosticsFilter.filter(message)
            LspFraming.writeMessage(ideOut, filtered)
        }
    }

    runCatching { ideOut.flush() }
    runCatching { toServer.interrupt() }
    exitProcess(runCatching { server.waitFor() }.getOrDefault(0))
}
