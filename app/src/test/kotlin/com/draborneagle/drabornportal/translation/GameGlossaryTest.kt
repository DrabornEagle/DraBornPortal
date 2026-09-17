package com.draborneagle.drabornportal.translation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameGlossaryTest {
    @Test
    fun protectsAndRestoresProperNouns() {
        val protected = GameGlossary.protect("Calm the Mystical Eye of Oculus")
        assertFalse(protected.text.contains("Oculus"))
        assertTrue(protected.replacements.isNotEmpty())

        val token = protected.replacements.keys.first()
        val restored = GameGlossary.restore("Mistik Gözü sakinleştir $token", protected.replacements)
        assertEquals("Mistik Gözü sakinleştir Oculus", restored)
    }
}
