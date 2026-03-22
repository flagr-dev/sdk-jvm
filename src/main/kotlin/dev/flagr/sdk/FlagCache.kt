package dev.flagr.sdk

import dev.flagr.sdk.model.FlagValue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

internal class FlagCache {
    private val flags = ConcurrentHashMap<String, FlagValue>()

    // listeners keyed by flagKey; null key = wildcard (all flags)
    private val listeners = ConcurrentHashMap<String, CopyOnWriteArrayList<(String, Boolean) -> Unit>>()

    fun seed(snapshot: Map<String, FlagValue>) {
        flags.putAll(snapshot)
    }

    /** Returns changed flag keys after applying an update, so callers can fire listeners. */
    fun applyUpdate(flagKey: String, updated: FlagValue): Boolean {
        val previous = flags.put(flagKey, updated)
        return previous != updated
    }

    fun get(flagKey: String): FlagValue? = flags[flagKey]

    fun snapshot(): Map<String, FlagValue> = HashMap(flags)

    fun addListener(flagKey: String, listener: (flagKey: String, enabled: Boolean) -> Unit): Subscription {
        listeners.getOrPut(flagKey) { CopyOnWriteArrayList() }.add(listener)
        return Subscription { listeners[flagKey]?.remove(listener) }
    }

    fun fireListeners(flagKey: String, tenantId: String) {
        val enabled = resolve(flagKey, tenantId, false)
        listeners[flagKey]?.forEach { it(flagKey, enabled) }
    }

    fun resolve(flagKey: String, tenantId: String, default: Boolean): Boolean {
        val flag = flags[flagKey] ?: return default
        return when (flag.state) {
            "enabled" -> true
            "disabled" -> false
            "partially_enabled" -> flag.enabledList.contains(tenantId)
            else -> default
        }
    }
}
