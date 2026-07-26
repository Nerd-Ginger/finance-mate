package dev.financemate.ui

import dev.financemate.ai.transport.AiEffort
import dev.financemate.ai.transport.AiModel
import dev.financemate.ai.transport.AiRequest
import dev.financemate.ai.transport.AiTransport
import dev.financemate.ai.transport.AnthropicTransport

/**
 * Holds the app's AI configuration.
 *
 * AI is off until the user supplies their own Anthropic API key, so the key
 * provider returns null and every call fails fast with MissingApiKeyException.
 * Phase 4 replaces the provider with a Keystore-backed, biometric-gated read.
 */
object AiStatus {

    /** True once the user has stored a key and enabled AI features. */
    val isConfigured: Boolean get() = false

    val selectedModel: AiModel = AiModel.DEFAULT

    val defaultEffort: AiEffort = AiEffort.HIGH

    val transport: AiTransport = AnthropicTransport(
        apiKeyProvider = { null },
    )

    /**
     * Shape of the first real call, kept here so the request type stays exercised
     * by the build while the feature layer is still being written.
     */
    fun probeRequest(): AiRequest = AiRequest(
        featureId = "connectivity-probe",
        userContent = "Reply with the single word: OK",
        model = selectedModel,
        effort = AiEffort.LOW,
        maxTokens = 16,
        cacheSystemPrompt = false,
    )
}
