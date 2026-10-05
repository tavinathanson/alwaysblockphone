package io.github.tavinathanson.alwaysblockphone.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class SessionsTest {
    private val min = 60_000L
    private val timing = Timing(waitMs = 5 * min, durationMs = 20 * min, cooldownMs = 30 * min)
    private val t0 = 1_800_000_000_000L

    private fun status(session: Session?, now: Long) = Sessions.status(timing, session, now)

    @Test fun blockedByDefault() {
        assertEquals(Status.Blocked, status(null, t0))
    }

    @Test fun fullLifecycle() {
        val s = Sessions.request(timing, null, t0)!!
        assertEquals(Status.Waiting(t0 + 5 * min), status(s, t0))
        assertEquals(Status.Waiting(t0 + 5 * min), status(s, t0 + 5 * min - 1))
        assertEquals(Status.Active(t0 + 25 * min), status(s, t0 + 5 * min))
        assertEquals(Status.Active(t0 + 25 * min), status(s, t0 + 25 * min - 1))
        assertEquals(Status.Cooldown(t0 + 55 * min), status(s, t0 + 25 * min))
        assertEquals(Status.Cooldown(t0 + 55 * min), status(s, t0 + 55 * min - 1))
        assertEquals(Status.Blocked, status(s, t0 + 55 * min))
    }

    @Test fun repeatedRequestsDoNotResetOrSkipAnything() {
        val s = Sessions.request(timing, null, t0)
        for (now in listOf(t0 + min, t0 + 10 * min, t0 + 30 * min)) {
            assertSame(s, Sessions.request(timing, s, now))
        }
    }

    @Test fun canRequestAgainAfterCooldown() {
        val first = Sessions.request(timing, null, t0)
        val later = t0 + 55 * min
        val second = Sessions.request(timing, first, later)!!
        assertEquals(Session(later, later + 5 * min, later + 25 * min), second)
    }

    @Test fun zeroWaitIsImmediatelyActive() {
        val instant = timing.copy(waitMs = 0)
        val s = Sessions.request(instant, null, t0)
        assertEquals(Status.Active(t0 + 20 * min), Sessions.status(instant, s, t0))
    }

    @Test fun zeroCooldownReturnsStraightToBlocked() {
        val noCooldown = timing.copy(cooldownMs = 0)
        val s = Sessions.request(noCooldown, null, t0)
        assertEquals(Status.Blocked, Sessions.status(noCooldown, s, t0 + 25 * min))
    }

    @Test fun cancellingAWaitReturnsToBlockedWithoutCooldown() {
        val s = Sessions.request(timing, null, t0)
        assertNull(Sessions.cancel(timing, s, t0 + min))
    }

    @Test fun endingAnActiveSessionStartsCooldownNow() {
        val s = Sessions.request(timing, null, t0)
        val end = t0 + 10 * min
        val ended = Sessions.cancel(timing, s, end)
        assertEquals(Status.Cooldown(end + 30 * min), status(ended, end))
    }

    @Test fun cancelDuringCooldownChangesNothing() {
        val s = Sessions.request(timing, null, t0)
        assertSame(s, Sessions.cancel(timing, s, t0 + 30 * min))
    }

    @Test fun reconcileDropsFinishedSessions() {
        val s = Sessions.request(timing, null, t0)
        assertSame(s, Sessions.reconcile(timing, s, t0 + 54 * min))
        assertNull(Sessions.reconcile(timing, s, t0 + 55 * min))
    }

    @Test fun clockMovedBackwardsBecomesAFreshCooldownNotAccess() {
        val s = Sessions.request(timing, null, t0)
        val earlier = t0 - 3 * 60 * min
        val r = Sessions.reconcile(timing, s, earlier)
        assertEquals(Status.Cooldown(earlier + 30 * min), status(r, earlier))
    }

    @Test fun smallBackwardsClockChangeWithinSessionIsHarmless() {
        val s = Sessions.request(timing, null, t0)
        assertSame(s, Sessions.reconcile(timing, s, t0 + 4 * min))
        assertEquals(Status.Waiting(t0 + 5 * min), status(s, t0 + 4 * min))
    }

    @Test fun sessionSurvivesEncodeDecode() {
        val s = Sessions.request(timing, null, t0)!!
        assertEquals(s, Session.decode(s.encode()))
    }

    @Test fun corruptStoredStateFailsSafeToBlocked() {
        for (bad in listOf(null, "", "garbage", "1,2", "1,2,3,4", "5,4,3", "a,b,c")) {
            val decoded = Session.decode(bad)
            assertNull("decode($bad)", decoded)
            assertEquals(Status.Blocked, status(decoded, t0))
        }
    }
}
