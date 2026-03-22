package dev.flagr.sdk

import dev.flagr.sdk.model.FlagUpdate
import dev.flagr.sdk.model.FlagValue
import kotlinx.coroutines.delay
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

private const val RECONNECT_DELAY_MS = 5_000L

internal class SseConnection(
    private val baseUrl: String,
    private val sdkKey: String,
    private val cache: FlagCache,
    private val httpClient: OkHttpClient,
    private val onFlagChanged: (flagKey: String) -> Unit,
) {
    private val json = Json { ignoreUnknownKeys = true }

    // Returns false when the caller should stop retrying (401).
    suspend fun runLoop() {
        while (true) {
            val shouldRetry = connect()
            if (!shouldRetry) return
            delay(RECONNECT_DELAY_MS)
        }
    }

    /** @return true = retry, false = stop permanently */
    private fun connect(): Boolean {
        val request = Request.Builder()
            .url("$baseUrl/evaluate/stream")
            .header("Authorization", "Bearer $sdkKey")
            .build()

        val response: Response = try {
            httpClient.newCall(request).execute()
        } catch (e: IOException) {
            return true // network error, retry
        }

        if (response.code == 401) {
            response.close()
            return false
        }

        if (!response.isSuccessful) {
            response.close()
            return true
        }

        return try {
            processStream(response)
            true // stream ended cleanly, reconnect
        } catch (e: IOException) {
            true
        } finally {
            response.close()
        }
    }

    private fun processStream(response: Response): Unit {
        val body = response.body ?: return
        val reader = BufferedReader(InputStreamReader(body.byteStream()))

        var eventType = ""
        val dataLines = StringBuilder()

        reader.forEachLine { raw ->
            val line = raw.trimEnd()
            when {
                line.startsWith("event:") -> {
                    eventType = line.removePrefix("event:").trim()
                }
                line.startsWith("data:") -> {
                    dataLines.append(line.removePrefix("data:").trim())
                }
                line.isEmpty() && dataLines.isNotEmpty() -> {
                    // Dispatch complete event
                    val data = dataLines.toString()
                    dataLines.clear()
                    when (eventType) {
                        "init" -> handleInit(data)
                        "flag_update" -> handleFlagUpdate(data)
                    }
                    eventType = ""
                }
            }
        }
    }

    private fun handleInit(data: String) {
        val snapshot = json.decodeFromString(
            MapSerializer(String.serializer(), FlagValue.serializer()),
            data,
        )
        val previous = cache.snapshot()
        cache.seed(snapshot)

        // Fire onChange for any flags whose state changed vs the previous snapshot.
        // On first connect the previous snapshot is empty, so all flags fire.
        for ((key, value) in snapshot) {
            if (previous[key] != value) {
                onFlagChanged(key)
            }
        }
    }

    private fun handleFlagUpdate(data: String) {
        val update = json.decodeFromString(FlagUpdate.serializer(), data)
        val flagValue = FlagValue(state = update.state, enabledList = update.enabledList)
        val changed = cache.applyUpdate(update.flagKey, flagValue)
        if (changed) onFlagChanged(update.flagKey)
    }
}
