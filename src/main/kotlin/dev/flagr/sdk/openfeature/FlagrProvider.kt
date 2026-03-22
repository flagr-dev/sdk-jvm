package dev.flagr.sdk.openfeature

import dev.flagr.sdk.FlagrClient
import dev.openfeature.sdk.EvaluationContext
import dev.openfeature.sdk.FeatureProvider
import dev.openfeature.sdk.Hook
import dev.openfeature.sdk.Metadata
import dev.openfeature.sdk.ProviderEvaluation
import dev.openfeature.sdk.Reason
import dev.openfeature.sdk.Value
import dev.openfeature.sdk.exceptions.InvalidContextError

/**
 * OpenFeature provider backed by [FlagrClient].
 *
 * ```kotlin
 * val provider = FlagrProvider(sdkKey = "sdk_live_...")
 * OpenFeatureAPI.getInstance().setProvider(provider)
 *
 * val client = OpenFeatureAPI.getInstance().getClient()
 * val enabled = client.getBooleanValue("my-flag", false, ctx)
 * ```
 *
 * `targetingKey` in the [EvaluationContext] maps to `tenant_id` in flagr.
 */
class FlagrProvider(
    sdkKey: String,
    baseUrl: String = "https://api.flagr.dev",
) : FeatureProvider {

    val flagrClient = FlagrClient(sdkKey = sdkKey, baseUrl = baseUrl)

    override fun getMetadata(): Metadata = Metadata { "flagr" }

    override fun getProviderHooks(): List<Hook<*>> = emptyList()

    override fun initialize(evaluationContext: EvaluationContext?) {}

    override fun shutdown() {
        flagrClient.close()
    }

    override fun getBooleanEvaluation(
        key: String,
        defaultValue: Boolean,
        evaluationContext: EvaluationContext?,
    ): ProviderEvaluation<Boolean> {
        val tenantId = evaluationContext?.targetingKey
            ?: throw InvalidContextError("targetingKey (tenant_id) is required")

        val value = flagrClient.isEnabled(key, tenantId, defaultValue)
        return ProviderEvaluation.builder<Boolean>()
            .value(value)
            .reason(Reason.CACHED.toString())
            .build()
    }

    // Flagr is boolean-only; all other types delegate to getBooleanEvaluation.

    override fun getStringEvaluation(
        key: String,
        defaultValue: String,
        evaluationContext: EvaluationContext?,
    ): ProviderEvaluation<String> {
        val bool = getBooleanEvaluation(key, defaultValue == "true", evaluationContext)
        return ProviderEvaluation.builder<String>()
            .value(bool.value.toString())
            .reason(bool.reason)
            .build()
    }

    override fun getIntegerEvaluation(
        key: String,
        defaultValue: Int,
        evaluationContext: EvaluationContext?,
    ): ProviderEvaluation<Int> {
        val bool = getBooleanEvaluation(key, defaultValue != 0, evaluationContext)
        return ProviderEvaluation.builder<Int>()
            .value(if (bool.value) 1 else 0)
            .reason(bool.reason)
            .build()
    }

    override fun getDoubleEvaluation(
        key: String,
        defaultValue: Double,
        evaluationContext: EvaluationContext?,
    ): ProviderEvaluation<Double> {
        val bool = getBooleanEvaluation(key, defaultValue != 0.0, evaluationContext)
        return ProviderEvaluation.builder<Double>()
            .value(if (bool.value) 1.0 else 0.0)
            .reason(bool.reason)
            .build()
    }

    override fun getObjectEvaluation(
        key: String,
        defaultValue: Value,
        evaluationContext: EvaluationContext?,
    ): ProviderEvaluation<Value> {
        val bool = getBooleanEvaluation(key, false, evaluationContext)
        return ProviderEvaluation.builder<Value>()
            .value(Value(bool.value))
            .reason(bool.reason)
            .build()
    }
}
