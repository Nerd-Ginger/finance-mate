package dev.financemate.ai.transport

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.models.messages.CacheControlEphemeral
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.TextBlockParam
import com.anthropic.models.messages.ThinkingConfigAdaptive
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/**
 * Calls the Anthropic Messages API directly from the device with the user's own
 * API key.
 *
 * The key is supplied per call by [apiKeyProvider] rather than held as a field so
 * that it can be fetched from the hardware Keystore behind a biometric prompt at
 * the moment of use, and never sits in this object's memory longer than a
 * request takes.
 */
public class AnthropicTransport(
    private val apiKeyProvider: suspend () -> String?,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AiTransport {

    private val clientLock = Any()

    @Volatile
    private var cachedClient: AnthropicClient? = null

    /**
     * Fingerprint of the key the cached client was built with, so a rotated key
     * is detected without this class keeping a second copy of the secret. The SDK
     * necessarily holds the key internally to sign requests; there is no reason
     * for us to hold one too.
     */
    @Volatile
    private var cachedKeyFingerprint: String? = null

    override suspend fun complete(request: AiRequest): AiResponse = withContext(ioDispatcher) {
        val apiKey = apiKeyProvider() ?: throw MissingApiKeyException()

        try {
            val client = clientFor(apiKey)
            val message = client.messages().create(buildParams(request))

            val text = message.content()
                .mapNotNull { block -> block.text().orElse(null)?.text() }
                .joinToString(separator = "")

            AiResponse(
                text = text,
                modelId = message.model().toString(),
                stopReason = message.stopReason().map { it.toString() }.orElse(null),
                usage = AiUsage(
                    inputTokens = message.usage().inputTokens(),
                    outputTokens = message.usage().outputTokens(),
                    cacheCreationInputTokens =
                        message.usage().cacheCreationInputTokens().orElse(0L),
                    cacheReadInputTokens =
                        message.usage().cacheReadInputTokens().orElse(0L),
                ),
            )
        } catch (e: Exception) {
            // Never let the exception text carry the request body onward: a crash
            // reporter or log sink would then hold financial data that the
            // redaction gate was built to keep off the wire.
            throw AiTransportException(
                "Anthropic request failed for feature '${request.featureId}': ${e.javaClass.simpleName}",
                e,
            )
        }
    }

    /**
     * Returns a client for [apiKey], reusing the previous one when the key is
     * unchanged. Building a fresh client per request would spin up a new OkHttp
     * connection pool and dispatcher thread pool every time and discard warm TLS
     * connections — expensive on a phone, and a slow leak under repeated calls.
     */
    private fun clientFor(apiKey: String): AnthropicClient {
        val fingerprint = fingerprintOf(apiKey)
        cachedClient?.let { existing ->
            if (cachedKeyFingerprint == fingerprint) return existing
        }
        return synchronized(clientLock) {
            val current = cachedClient
            if (current != null && cachedKeyFingerprint == fingerprint) {
                current
            } else {
                AnthropicOkHttpClient.builder()
                    .apiKey(apiKey)
                    .build()
                    .also {
                        cachedClient = it
                        cachedKeyFingerprint = fingerprint
                    }
            }
        }
    }

    private fun fingerprintOf(apiKey: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(apiKey.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte) }

    private fun buildParams(request: AiRequest): MessageCreateParams {
        val builder = MessageCreateParams.builder()
            .model(request.model.id)
            .maxTokens(request.maxTokens)
            // Adaptive thinking lets the model decide how much reasoning a given
            // request warrants, instead of paying for a fixed budget every time.
            .thinking(ThinkingConfigAdaptive.builder().build())
            .outputConfig(
                OutputConfig.builder()
                    .effort(request.effort.toApiEffort())
                    .build(),
            )
            .addUserMessage(request.userContent)

        request.systemPrompt?.let { prompt ->
            if (request.cacheSystemPrompt) {
                // FinanceMate's system prompts are large and stable (a category
                // taxonomy, mostly) while the user content changes every call.
                // Caching the prefix turns repeat calls into cache reads at ~0.1x
                // input price. The breakpoint goes on the system block so the
                // volatile part stays after it.
                builder.systemOfTextBlockParams(
                    listOf(
                        TextBlockParam.builder()
                            .text(prompt)
                            .cacheControl(CacheControlEphemeral.builder().build())
                            .build(),
                    ),
                )
            } else {
                builder.system(prompt)
            }
        }

        return builder.build()
    }
}

private fun AiEffort.toApiEffort(): OutputConfig.Effort = when (this) {
    AiEffort.LOW -> OutputConfig.Effort.LOW
    AiEffort.MEDIUM -> OutputConfig.Effort.MEDIUM
    AiEffort.HIGH -> OutputConfig.Effort.HIGH
    AiEffort.XHIGH -> OutputConfig.Effort.XHIGH
    AiEffort.MAX -> OutputConfig.Effort.MAX
}
