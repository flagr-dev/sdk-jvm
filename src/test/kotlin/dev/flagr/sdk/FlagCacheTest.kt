package dev.flagr.sdk

import dev.flagr.sdk.model.FlagValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FlagCacheTest {

    private fun cache(vararg pairs: Pair<String, FlagValue>) = FlagCache().also {
        it.seed(mapOf(*pairs))
    }

    @Test
    fun `enabled flag returns true for any tenant`() {
        val c = cache("feat" to FlagValue("enabled"))
        assertTrue(c.resolve("feat", "any-tenant", false))
    }

    @Test
    fun `disabled flag returns false for any tenant`() {
        val c = cache("feat" to FlagValue("disabled"))
        assertFalse(c.resolve("feat", "any-tenant", true))
    }

    @Test
    fun `partial rollout returns true only for listed tenant`() {
        val c = cache("feat" to FlagValue("partially_enabled", listOf("tenant-a")))
        assertTrue(c.resolve("feat", "tenant-a", false))
        assertFalse(c.resolve("feat", "tenant-b", false))
    }

    @Test
    fun `unknown flag returns default`() {
        val c = FlagCache()
        assertTrue(c.resolve("missing", "t", true))
        assertFalse(c.resolve("missing", "t", false))
    }

    @Test
    fun `applyUpdate replaces value`() {
        val c = cache("feat" to FlagValue("disabled"))
        val changed = c.applyUpdate("feat", FlagValue("enabled"))
        assertTrue(changed)
        assertTrue(c.resolve("feat", "t", false))
    }

    @Test
    fun `applyUpdate with same value returns false`() {
        val v = FlagValue("enabled")
        val c = cache("feat" to v)
        val changed = c.applyUpdate("feat", v)
        assertFalse(changed)
    }

    @Test
    fun `snapshot returns current state`() {
        val c = cache("a" to FlagValue("enabled"), "b" to FlagValue("disabled"))
        val snap = c.snapshot()
        assertEquals(2, snap.size)
        assertEquals("enabled", snap["a"]?.state)
    }
}
