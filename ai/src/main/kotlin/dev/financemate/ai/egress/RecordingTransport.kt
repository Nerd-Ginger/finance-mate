package dev.financemate.ai.egress

import dev.financemate.ai.transport.AiRequest
import dev.financemate.ai.transport.AiResponse
import dev.financemate.ai.transport.AiTransport
import java.time.Clock
import java.time.Instant

/**
 * Wraps a transport so that nothing reaches the network unrecorded.
 *
 * The ordering is the whole point: the attempt is written first, the delegate is
 * called second. If those were the other way round, a request that hung or a
 * process that was killed mid-flight would leave no trace, and "every request is
 * in this log" would become "every request that came back is in this log" —
 * which is a much weaker promise, and not the one the app makes.
 *
 * Recording failures do not swallow the request, and request failures do not
 * swallow the record. Both are written; the outcome distinguishes them.
 */
public class RecordingTransport(
    private val delegate: AiTransport,
    private val recorder: EgressRecorder,
    private val endpoint: String = ANTHROPIC_MESSAGES,
    private val clock: Clock = Clock.systemUTC(),
) : AiTransport {

    override suspend fun complete(request: AiRequest): AiResponse {
        val id = recorder.recordAttempt(
            EgressAttempt(
                occurredAt = Instant.now(clock),
                featureId = request.featureId,
                endpoint = endpoint,
                modelId = request.model.id,
                payload = payloadOf(request),
            ),
        )

        val response = try {
            delegate.complete(request)
        } catch (e: Exception) {
            // Class name only. The message could carry the request body, and the
            // log is user-readable storage, not a place to widen the blast radius
            // of an exception.
            recorder.recordOutcome(id, EgressOutcome.Failed(e.javaClass.simpleName))
            throw e
        }

        recorder.recordOutcome(
            id,
            EgressOutcome.Completed(
                responseBytes = response.text.toByteArray(Charsets.UTF_8).size,
                inputTokens = response.usage.inputTokens,
                outputTokens = response.usage.outputTokens,
            ),
        )
        return response
    }

    /**
     * The full text sent to the model, system prompt included.
     *
     * The system prompt is part of the payload even though it is ours rather than
     * the user's. Showing only the user content would let a future system prompt
     * carry data off the device without appearing in a log that claims to be
     * complete.
     */
    private fun payloadOf(request: AiRequest): String =
        when (val system = request.systemPrompt) {
            null -> request.userContent
            else -> "system:\n$system\n\nuser:\n${request.userContent}"
        }

    public companion object {
        public const val ANTHROPIC_MESSAGES: String = "https://api.anthropic.com/v1/messages"
    }
}
