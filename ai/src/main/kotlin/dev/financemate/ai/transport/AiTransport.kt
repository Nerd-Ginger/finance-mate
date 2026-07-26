package dev.financemate.ai.transport

/**
 * The single seam through which FinanceMate talks to a language model.
 *
 * Everything above this interface deals in already-redacted payloads. Everything
 * below it is transport detail: which SDK, which endpoint, whose key. Keeping the
 * seam here means the BYOK-direct-to-Anthropic arrangement can later be swapped
 * for a proxy without touching a single feature.
 *
 * Implementations must not perform redaction themselves — by the time a request
 * reaches a transport it is already sanitised, and a transport that "helpfully"
 * cleaned up its input would hide gaps in the real gate.
 */
public interface AiTransport {

    /** Sends one request and waits for the complete response. */
    public suspend fun complete(request: AiRequest): AiResponse
}

/** Models FinanceMate is willing to call, with their published per-MTok prices. */
public enum class AiModel(
    public val id: String,
    public val displayName: String,
    public val inputPricePerMTokUsd: String,
    public val outputPricePerMTokUsd: String,
) {
    /** Default. Strongest reasoning; the right choice for analysis and chat. */
    OPUS_5(
        id = "claude-opus-5",
        displayName = "Claude Opus 5",
        inputPricePerMTokUsd = "5.00",
        outputPricePerMTokUsd = "25.00",
    ),

    /**
     * Cheapest option, offered because the user is paying for their own key and
     * bulk merchant classification is a simple, high-volume task where the price
     * difference is material.
     */
    HAIKU_4_5(
        id = "claude-haiku-4-5",
        displayName = "Claude Haiku 4.5",
        inputPricePerMTokUsd = "1.00",
        outputPricePerMTokUsd = "5.00",
    ),
    ;

    public companion object {
        public val DEFAULT: AiModel = OPUS_5
    }
}

/**
 * How hard the model should work. Maps to the API's `output_config.effort`.
 *
 * Lower effort means fewer tokens and lower latency at some cost in quality; it
 * is the main lever for keeping a BYOK user's bill predictable.
 */
public enum class AiEffort {
    LOW,
    MEDIUM,
    HIGH,
    XHIGH,
    MAX,
}

/**
 * A sanitised request, ready to leave the device.
 *
 * @property featureId identifies which feature raised this call. Used to key the
 *   one-time payload-preview consent and to attribute entries in the egress log.
 */
public data class AiRequest(
    val featureId: String,
    val userContent: String,
    val systemPrompt: String? = null,
    val model: AiModel = AiModel.DEFAULT,
    val effort: AiEffort = AiEffort.HIGH,
    val maxTokens: Long = 8_192,
    val cacheSystemPrompt: Boolean = true,
)

public data class AiResponse(
    val text: String,
    val usage: AiUsage,
    val modelId: String,
    val stopReason: String?,
)

public data class AiUsage(
    val inputTokens: Long,
    val outputTokens: Long,
    val cacheCreationInputTokens: Long,
    val cacheReadInputTokens: Long,
)

/** Raised when a transport cannot complete a request. */
public class AiTransportException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/** Raised when the user has not yet supplied an API key. */
public class MissingApiKeyException : Exception(
    "No Anthropic API key has been configured. AI features stay disabled until one is added.",
)
