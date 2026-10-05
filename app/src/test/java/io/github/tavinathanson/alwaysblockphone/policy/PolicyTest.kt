package io.github.tavinathanson.alwaysblockphone.policy

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicyTest {
    @Test fun parsesAllAccessLevels() {
        val policy = Policy.parse(
            """
            # comment
            signal     normal     org.thoughtcrime.securesms
            insta      FRICTION   com.instagram.android  5 20 30   # trailing comment
            yt         absent     com.google.android.youtube
            mail       undecided  com.example.mail
            """.trimIndent()
        )
        assertEquals(listOf(Access.NORMAL, Access.FRICTION, Access.ABSENT, Access.UNDECIDED), policy.rules.map { it.access })
        assertEquals(Timing(300_000, 1_200_000, 1_800_000), policy.rule("insta")!!.timing)
        assertEquals("insta", policy.frictionRuleFor("com.instagram.android")!!.id)
        assertNull(policy.frictionRuleFor("org.thoughtcrime.securesms"))
        assertEquals("Insta", policy.rule("insta")!!.displayName)
    }

    @Test fun acceptsFractionalMinutes() {
        val t = Policy.parse("x friction a.b 0.5 1 0").rules.single().timing!!
        assertEquals(Timing(30_000, 60_000, 0), t)
    }

    @Test fun rejectsBadLines() {
        val bad = listOf(
            "x friction a.b",                // missing timing
            "x friction a.b 1 2",            // incomplete timing
            "x friction a.b 1 0 1",          // zero duration
            "x friction a.b -1 2 3",         // negative
            "x friction a.b 1 2 NaN",        // not a number
            "x normal a.b 1 2 3",            // timing on a non-friction rule
            "x blocked a.b",                 // unknown access
            "x normal notapackage",          // invalid package
            "x normal",                      // too few columns
            "x normal a.b\nx absent c.d",    // duplicate id
            "x normal a.b\ny absent a.b",    // duplicate package
        )
        for (text in bad) {
            assertThrows(text, IllegalArgumentException::class.java) { Policy.parse(text) }
        }
    }

    /** The shipped example (and a local override, if present) must always parse. */
    @Test fun shippedPolicyFilesParse() {
        val dir = File("../policy")
        val files = Policy.FILE_NAMES.map { File(dir, it) }.filter { it.exists() }
        assertTrue("policy/policy.conf missing", files.any { it.name == "policy.conf" })
        files.forEach { Policy.parse(it.readText()) }
    }
}
