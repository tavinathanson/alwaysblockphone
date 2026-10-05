package io.github.tavinathanson.alwaysblockphone.policy

/** How an app should be reachable on the device. */
enum class Access { NORMAL, FRICTION, ABSENT, UNDECIDED }

/** Friction timing, in milliseconds. */
data class Timing(val waitMs: Long, val durationMs: Long, val cooldownMs: Long)

data class AppRule(
    val id: String,
    val access: Access,
    val packageName: String,
    /** Present only for [Access.FRICTION] rules. */
    val timing: Timing? = null,
) {
    val displayName: String get() = id.replaceFirstChar { it.uppercase() }
}

data class Policy(val rules: List<AppRule>) {
    val frictionRules: List<AppRule> get() = rules.filter { it.access == Access.FRICTION }

    fun frictionRuleFor(packageName: String): AppRule? =
        frictionRules.firstOrNull { it.packageName == packageName }

    fun rule(id: String): AppRule? = rules.firstOrNull { it.id == id }

    companion object {
        /** The policy file the app reads; a gitignored local.conf overrides the example. */
        val FILE_NAMES = listOf("local.conf", "policy.conf")

        private const val MINUTE_MS = 60_000L

        /**
         * Parses the whitespace-separated policy table described in policy/policy.conf.
         * Throws [IllegalArgumentException] naming the offending line on any error.
         */
        fun parse(text: String): Policy {
            val rules = text.lineSequence()
                .mapIndexedNotNull { index, raw ->
                    val line = raw.substringBefore('#').trim()
                    if (line.isEmpty()) null else parseLine(line, index + 1)
                }
                .toList()
            requireUnique(rules.map { it.id }, "id")
            requireUnique(rules.map { it.packageName }, "package")
            return Policy(rules)
        }

        private fun parseLine(line: String, lineNo: Int): AppRule {
            val cols = line.split(Regex("\\s+"))
            fun fail(msg: String): Nothing = throw IllegalArgumentException("policy line $lineNo: $msg")

            if (cols.size < 3) fail("expected at least: id access package")
            val (id, accessText, pkg) = cols
            val access = Access.entries.firstOrNull { it.name.equals(accessText, ignoreCase = true) }
                ?: fail("unknown access '$accessText'")
            if (!pkg.matches(Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+"))) fail("invalid package '$pkg'")

            val numbers = cols.drop(3)
            if (access != Access.FRICTION) {
                if (numbers.isNotEmpty()) fail("only friction rules take timing columns")
                return AppRule(id, access, pkg)
            }
            if (numbers.size != 3) fail("friction needs: wait duration cooldown (minutes)")
            val (wait, duration, cooldown) = numbers.map { text ->
                val minutes = text.toDoubleOrNull()?.takeIf { it >= 0 && it.isFinite() }
                    ?: fail("'$text' is not a non-negative number of minutes")
                (minutes * MINUTE_MS).toLong()
            }
            if (duration == 0L) fail("duration must be above zero")
            return AppRule(id, access, pkg, Timing(wait, duration, cooldown))
        }

        private fun requireUnique(values: List<String>, what: String) {
            val dupes = values.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
            require(dupes.isEmpty()) { "policy has duplicate $what: ${dupes.joinToString()}" }
        }
    }
}
