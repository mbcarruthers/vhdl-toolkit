package dev.vhdltoolkit.diagnostics

import dev.vhdltoolkit.diagnostics.DiagnosticDebouncer.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticDebouncerTest {

    private val a = "file:///a.vhd"
    private val b = "file:///b.vhd"

    private fun debouncer() = DiagnosticDebouncer(quietPeriodMs = 400, maxHoldMs = 2000)

    @Test
    fun theFirstSetIsHeldForTheQuietPeriod() {
        assertEquals(Decision.After(400), debouncer().arrived(a, 0))
    }

    @Test
    fun aSetArrivingDuringTheHoldRestartsTheWait() {
        val d = debouncer()
        d.arrived(a, 0)
        // A keystroke 100ms in: the display slides out to 500ms, because what
        // is on screen would otherwise be a file state already superseded.
        assertEquals(Decision.After(400), d.arrived(a, 100))
        assertEquals(Decision.After(400), d.arrived(a, 200))
    }

    @Test
    fun continuousTypingStillGetsFeedbackAtTheCap() {
        val d = debouncer()
        d.arrived(a, 0)
        var t = 0L
        // Keystrokes every 100ms, which never leave a 400ms gap.
        while (t < 1900) {
            t += 100
            assertTrue("held at ${t}ms", d.arrived(a, t) is Decision.After)
        }
        assertEquals("the cap must break the deadlock", Decision.Now, d.arrived(a, 2000))
    }

    @Test
    fun theDelayIsTrimmedSoTheCapIsNotOvershot() {
        val d = debouncer()
        d.arrived(a, 0)
        // 1800ms in, a full quiet period would land at 2200 — past the cap.
        assertEquals(Decision.After(200), d.arrived(a, 1800))
    }

    @Test
    fun showingASetStartsTheRunAgain() {
        val d = debouncer()
        d.arrived(a, 0)
        d.arrived(a, 1500)
        d.released(a)
        // The cap is measured from here, not from the earlier run.
        assertEquals(Decision.After(400), d.arrived(a, 1600))
        assertEquals(Decision.After(400), d.arrived(a, 3000))
        assertEquals(Decision.Now, d.arrived(a, 3600))
    }

    @Test
    fun filesAreHeldIndependently() {
        val d = debouncer()
        d.arrived(a, 0)
        // Typing in one file must not push another file towards its cap.
        assertEquals(Decision.After(400), d.arrived(b, 1900))
        assertEquals(Decision.Now, d.arrived(a, 2000))
        assertEquals(Decision.After(400), d.arrived(b, 2000))
    }

    @Test
    fun forgettingAFileClearsItsRun() {
        val d = debouncer()
        d.arrived(a, 0)
        d.forget(a)
        assertEquals(Decision.After(400), d.arrived(a, 5000))
    }

    @Test
    fun aPauseLongerThanTheCapShowsTheNextSetAtOnce() {
        val d = debouncer()
        d.arrived(a, 0)
        // Nothing released the hold, and the next edit comes much later: the
        // waiting set is already stale, so it goes out without further delay.
        assertEquals(Decision.Now, d.arrived(a, 60_000))
    }
}
