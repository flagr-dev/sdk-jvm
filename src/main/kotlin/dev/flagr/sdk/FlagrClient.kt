package dev.flagr.sdk

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Entry point for the Flagr JVM SDK.
 *
 * ```kotlin
 * val flagr = FlagrClient(sdkKey = "sdk_live_...", baseUrl = "https://api.flagr.dev")
 *
 * val enabled = flagr.isEnabled("my-flag", tenantId = "user-123")
 *
 * val sub = flagr.onChange("my-flag", tenantId = "user-123") { isEnabled ->
 *     println("flag changed: $isEnabled")
 * }
 * sub.unsubscribe()
 *
 * flagr.close() // stops the SSE connection
 * ```
 */
class FlagrClient(
    private val sdkKey: String,
    baseUrl: String = "https://api.flagr.dev",
    httpClient: OkHttpClient = OkHttpClient(),
) : Closeable {

    private val cache = FlagCache()
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    // flagKey → list of (tenantId, callback) pairs
    private val changeListeners =
        ConcurrentHashMap<String, CopyOnWriteArrayList<Pair<String, (Boolean) -> Unit>>>()

    private val connection = SseConnection(
        baseUrl = baseUrl.trimEnd('/'),
        sdkKey = sdkKey,
        cache = cache,
        httpClient = httpClient,
        onFlagChanged = { flagKey -> fireListeners(flagKey) },
    )

    init {
        scope.launch { connection.runLoop() }
    }

    /**
     * Evaluates a flag synchronously from the local cache.
     * Returns [default] if the flag is unknown (cache not yet populated).
     */
    fun isEnabled(flagKey: String, tenantId: String, default: Boolean = false): Boolean =
        cache.resolve(flagKey, tenantId, default)

    /**
     * Subscribes to changes for a flag+tenant pair.
     * The callback fires immediately with the current value, then on every SSE update.
     * Returns a [Subscription] — call [Subscription.unsubscribe] to stop receiving updates.
     */
    fun onChange(
        flagKey: String,
        tenantId: String,
        default: Boolean = false,
        callback: (Boolean) -> Unit,
    ): Subscription {
        val pair = Pair(tenantId, callback)
        changeListeners.getOrPut(flagKey) { CopyOnWriteArrayList() }.add(pair)

        // Fire immediately with current value
        callback(cache.resolve(flagKey, tenantId, default))

        return Subscription {
            changeListeners[flagKey]?.remove(pair)
        }
    }

    private fun fireListeners(flagKey: String) {
        val listeners = changeListeners[flagKey] ?: return
        for ((tenantId, callback) in listeners) {
            callback(cache.resolve(flagKey, tenantId, false))
        }
    }

    override fun close() {
        job.cancel()
    }
}
