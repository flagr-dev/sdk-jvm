package dev.flagr.sdk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FlagValue(
    val state: String,
    @SerialName("enabled_list")
    val enabledList: List<String> = emptyList(),
)
