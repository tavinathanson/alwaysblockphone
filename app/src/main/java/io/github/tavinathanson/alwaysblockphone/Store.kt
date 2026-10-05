package io.github.tavinathanson.alwaysblockphone

import android.annotation.SuppressLint
import android.content.Context
import io.github.tavinathanson.alwaysblockphone.policy.AppRule
import io.github.tavinathanson.alwaysblockphone.policy.Policy
import io.github.tavinathanson.alwaysblockphone.policy.Session
import io.github.tavinathanson.alwaysblockphone.policy.Sessions
import io.github.tavinathanson.alwaysblockphone.policy.Status

/**
 * The single place the app reads policy and session state. Both the UI and the
 * Accessibility backend go through it, so they cannot disagree.
 *
 * Session state is three timestamps per friction rule in private SharedPreferences.
 * Nothing about app usage is stored.
 */
class Store(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("sessions", Context.MODE_PRIVATE)

    /** The built-in policy, or the parse error message. */
    val policy: Result<Policy> get() = cachedPolicy ?: loadPolicy().also { cachedPolicy = it }

    fun status(rule: AppRule, now: Long = System.currentTimeMillis()): Status =
        Sessions.status(rule.friction, session(rule, now), now)

    fun request(rule: AppRule) = update(rule) { s, now -> Sessions.request(rule.friction, s, now) }

    fun cancel(rule: AppRule) = update(rule) { s, now -> Sessions.cancel(rule.friction, s, now) }

    private fun session(rule: AppRule, now: Long): Session? {
        val stored = prefs.getString(rule.id, null)
        val session = Sessions.reconcile(rule.friction, Session.decode(stored), now)
        if (session?.encode() != stored) save(rule, session)
        return session
    }

    private fun update(rule: AppRule, transform: (Session?, Long) -> Session?) {
        val now = System.currentTimeMillis()
        save(rule, transform(session(rule, now), now))
    }

    // commit() rather than apply(): state is tiny and must survive an immediate process kill.
    @SuppressLint("ApplySharedPref", "UseKtx")
    private fun save(rule: AppRule, session: Session?) {
        prefs.edit().apply { if (session == null) remove(rule.id) else putString(rule.id, session.encode()) }.commit()
    }

    private fun loadPolicy(): Result<Policy> = runCatching {
        val assets = app.assets.list("").orEmpty()
        val name = Policy.FILE_NAMES.first { it in assets }
        Policy.parse(app.assets.open(name).bufferedReader().use { it.readText() })
    }

    private val AppRule.friction get() = requireNotNull(timing) { "$id is not a friction rule" }

    private companion object {
        @Volatile var cachedPolicy: Result<Policy>? = null
    }
}
