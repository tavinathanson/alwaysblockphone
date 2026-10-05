package io.github.tavinathanson.alwaysblockphone.policy

/**
 * One request for access to a friction app. Every status is derived from these three
 * wall-clock timestamps plus the rule's [Timing], so nothing needs a running timer to
 * return to blocked: once the times pass, the app is blocked again.
 */
data class Session(val requestedAt: Long, val startsAt: Long, val endsAt: Long) {
    fun encode(): String = "$requestedAt,$startsAt,$endsAt"

    companion object {
        /** Returns null for missing or corrupt data, which fails safe to blocked. */
        fun decode(text: String?): Session? {
            val parts = text?.split(',')?.map { it.toLongOrNull() ?: return null } ?: return null
            if (parts.size != 3) return null
            val (requested, starts, ends) = parts
            return if (requested <= starts && starts <= ends) Session(requested, starts, ends) else null
        }
    }
}

sealed interface Status {
    /** No access and no pending request. The default. */
    data object Blocked : Status

    /** Access requested; it opens at [until]. */
    data class Waiting(val until: Long) : Status

    /** Access is open until [until]. */
    data class Active(val until: Long) : Status

    /** The last session ended; a new request is refused until [until]. */
    data class Cooldown(val until: Long) : Status
}

/** Pure state transitions for one friction rule. All functions take the current time explicitly. */
object Sessions {
    fun status(timing: Timing, session: Session?, now: Long): Status = when {
        session == null -> Status.Blocked
        now < session.startsAt -> Status.Waiting(session.startsAt)
        now < session.endsAt -> Status.Active(session.endsAt)
        now < session.endsAt + timing.cooldownMs -> Status.Cooldown(session.endsAt + timing.cooldownMs)
        else -> Status.Blocked
    }

    /** Starts a new request only from [Status.Blocked]; a repeated request never resets the wait. */
    fun request(timing: Timing, session: Session?, now: Long): Session? {
        if (status(timing, session, now) != Status.Blocked) return session
        val startsAt = now + timing.waitMs
        return Session(now, startsAt, startsAt + timing.durationMs)
    }

    /**
     * Cancelling a pending request returns to blocked with no cooldown (no access happened).
     * Ending an active session closes it now, which starts the cooldown.
     */
    fun cancel(timing: Timing, session: Session?, now: Long): Session? =
        when (status(timing, session, now)) {
            is Status.Waiting -> null
            is Status.Active -> session!!.copy(endsAt = now)
            else -> session
        }

    /**
     * Normalizes stored state against the current clock:
     * - drops sessions whose cooldown is over, so storage only holds live state;
     * - if the clock moved backwards past the request time, the session is treated as ended
     *   now, so a fresh cooldown starts. Without this a backwards clock change could leave an
     *   app stuck waiting for hours, or replay an access window that was already used. The
     *   result is bounded by the cooldown and never grants access.
     */
    fun reconcile(timing: Timing, session: Session?, now: Long): Session? = when {
        session == null -> null
        now < session.requestedAt -> Session(now, now, now)
        status(timing, session, now) == Status.Blocked -> null
        else -> session
    }
}
