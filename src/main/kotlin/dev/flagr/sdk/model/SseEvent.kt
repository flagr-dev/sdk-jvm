package dev.flagr.sdk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Payload of an `event: flag_update` SSE line.
@Serializable
data class FlagUpdate(
    @SerialName("flag_key")
    val flagKey: String,
    val state: String,
    @SerialName("enabled_list")
    val enabledList: List<String> = emptyList(),
)
