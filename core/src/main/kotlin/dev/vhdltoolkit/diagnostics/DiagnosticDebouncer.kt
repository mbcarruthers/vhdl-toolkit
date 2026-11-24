package dev.vhdltoolkit.diagnostics

/**
 * Decides when a freshly arrived set of diagnostics should reach the editor.
 *
 * The language server re-analyses on every keystroke and publishes the result
 * immediately, so half-written code is reported as broken: open a port list
 * and every line below it is wrong until the `)` is typed. The errors are
 * real, but they are about text the author has not finished writing, and they
 * bury the ones that matter.
 *
 * IntelliJ's own analyser solves this by waiting for a pause before it shows
 * anything. This does the same for the LSP path: each set is held briefly, and
 * a newer set replaces the held one rather than being queued behind it, so
 * during a burst of typing only the last set is ever shown.
 *
 * A cap stops that becoming silence. Someone typing without pause for longer
 * than [maxHoldMs] still gets feedback, because an unbroken burst would
 * otherwise defer the display indefinitely.
 *
 * The decision is kept separate from the timer that acts on it so it can be
 * tested with an explicit clock.
 */
class DiagnosticDebouncer(
    private val quietPeriodMs: Long = DEFAULT_QUIET_PERIOD_MS,
    private val maxHoldMs: Long = DEFAULT_MAX_HOLD_MS,
) {

    sealed interface Decision {
        /** Show this set now. */
        data object Now : Decision

        /**
         * Hold it this long. A set arriving before the delay elapses replaces
         * this one, and its own delay starts from scratch.
         */
        data class After(val delayMs: Long) : Decision
    }

    /** When the current unbroken run of publishes for a file began. */
    private val heldSince = HashMap<String, Long>()

    @Synchronized
    fun arrived(uri: String, nowMs: Long): Decision {
        val since = heldSince[uri]
        if (since == null) {
            heldSince[uri] = nowMs
            return Decision.After(quietPeriodMs)
        }

        val waited = nowMs - since
        if (waited >= maxHoldMs) {
            // Long enough. Show it and start a new run.
            heldSince.remove(uri)
            return Decision.Now
        }

        // The start of the run is deliberately left alone: the cap measures
        // how long the file has gone without feedback, not the gap since the
        // last keystroke. Shortening the delay to fit keeps the cap honest
        // when a burst ends just short of it.
        return Decision.After(minOf(quietPeriodMs, maxHoldMs - waited))
    }

    /** A held set has been shown; the next arrival begins a new run. */
    @Synchronized
    fun released(uri: String) {
        heldSince.remove(uri)
    }

    @Synchronized
    fun forget(uri: String) {
        heldSince.remove(uri)
    }

    companion object {
        /**
         * Long enough to cover the gap between keystrokes at speed, short
         * enough that a deliberate pause feels like an answer rather than a
         * wait.
         */
        const val DEFAULT_QUIET_PERIOD_MS = 400L

        /** Never leave a file without feedback for longer than this. */
        const val DEFAULT_MAX_HOLD_MS = 2_000L
    }
}
