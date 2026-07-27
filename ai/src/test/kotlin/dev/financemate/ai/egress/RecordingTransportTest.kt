package dev.financemate.ai.egress

import dev.financemate.ai.transport.AiModel
import dev.financemate.ai.transport.AiRequest
import dev.financemate.ai.transport.AiResponse
import dev.financemate.ai.transport.AiTransport
import dev.financemate.ai.transport.AiTransportException
import dev.financemate.ai.transport.AiUsage
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * The log's guarantee is "every request, including the ones that went wrong".
 * These tests are written against that sentence rather than against the
 * implementation, because it is the sentence the user is being asked to trust.
 */
class RecordingTransportTest {

    private val fixedClock = Clock.fixed(Instant.parse("2026-07-26T11:30:00Z"), ZoneOffset.UTC)

    @Test
    fun recordsTheAttemptBeforeSending() = runTest {
        val recorder = FakeRecorder()
        // A delegate that never returns stands in for a request that hangs, or a
        // process killed mid-flight. The attempt must already be on disk.
        val transport = RecordingTransport(
            delegate = NeverReturns(observed = recorder),
            recorder = recorder,
            clock = fixedClock,
        )

        shouldThrow<IllegalStateException> {
            transport.complete(request("classify-merchants", "ACME STORES"))
        }

        recorder.attempts.single().featureId shouldBe "classify-merchants"
        recorder.attemptWasRecordedBeforeSend shouldBe true
    }

    @Test
    fun recordsACompletedRequestWithItsSizes() = runTest {
        val recorder = FakeRecorder()
        val transport = RecordingTransport(
            delegate = Returns(text = "twelve chars"),
            recorder = recorder,
            clock = fixedClock,
        )

        transport.complete(request("classify-merchants", "ACME STORES"))

        val attempt = recorder.attempts.single()
        attempt.occurredAt shouldBe Instant.parse("2026-07-26T11:30:00Z")
        attempt.endpoint shouldBe RecordingTransport.ANTHROPIC_MESSAGES
        attempt.modelId shouldBe AiModel.OPUS_5.id
        attempt.payload shouldBe "ACME STORES"
        attempt.payloadBytes shouldBe 11

        recorder.outcomes.shouldContainExactly(
            0L to EgressOutcome.Completed(responseBytes = 12, inputTokens = 40, outputTokens = 5),
        )
    }

    @Test
    fun recordsAFailedRequestAndStillThrows() = runTest {
        val recorder = FakeRecorder()
        val transport = RecordingTransport(
            delegate = Fails(AiTransportException("Anthropic request failed")),
            recorder = recorder,
            clock = fixedClock,
        )

        shouldThrow<AiTransportException> {
            transport.complete(request("classify-merchants", "ACME STORES"))
        }

        recorder.attempts.size shouldBe 1
        recorder.outcomes.shouldContainExactly(
            0L to EgressOutcome.Failed("AiTransportException"),
        )
    }

    @Test
    fun failureRecordsCarryNoPayload() = runTest {
        val recorder = FakeRecorder()
        // An exception whose message contains the request body, which is exactly
        // the mistake that would turn the log into a second copy of the leak.
        val transport = RecordingTransport(
            delegate = Fails(IllegalArgumentException("rejected body: ACME STORES 42.19")),
            recorder = recorder,
            clock = fixedClock,
        )

        shouldThrow<IllegalArgumentException> {
            transport.complete(request("classify-merchants", "ACME STORES"))
        }

        val failure = recorder.outcomes.single().second as EgressOutcome.Failed
        failure.reason shouldBe "IllegalArgumentException"
        failure.reason shouldNotContain "ACME"
        failure.reason shouldNotContain "42.19"
    }

    @Test
    fun theSystemPromptIsPartOfTheLoggedPayload() = runTest {
        val recorder = FakeRecorder()
        val transport = RecordingTransport(
            delegate = Returns(text = "ok"),
            recorder = recorder,
            clock = fixedClock,
        )

        transport.complete(
            request("classify-merchants", "ACME STORES")
                .copy(systemPrompt = "You classify merchants."),
        )

        val payload = recorder.attempts.single().payload
        payload shouldContain "You classify merchants."
        payload shouldContain "ACME STORES"
    }

    // --- Fixtures ----------------------------------------------------------

    private fun request(featureId: String, content: String) =
        AiRequest(featureId = featureId, userContent = content)

    private class FakeRecorder : EgressRecorder {
        val attempts = mutableListOf<EgressAttempt>()
        val outcomes = mutableListOf<Pair<Long, EgressOutcome>>()
        var attemptWasRecordedBeforeSend = false

        override suspend fun recordAttempt(attempt: EgressAttempt): Long {
            attempts += attempt
            return (attempts.size - 1).toLong()
        }

        override suspend fun recordOutcome(id: Long, outcome: EgressOutcome) {
            outcomes += id to outcome
        }
    }

    private class Returns(private val text: String) : AiTransport {
        override suspend fun complete(request: AiRequest) = AiResponse(
            text = text,
            usage = AiUsage(
                inputTokens = 40,
                outputTokens = 5,
                cacheCreationInputTokens = 0,
                cacheReadInputTokens = 0,
            ),
            modelId = request.model.id,
            stopReason = "end_turn",
        )
    }

    private class Fails(private val error: Exception) : AiTransport {
        override suspend fun complete(request: AiRequest): AiResponse = throw error
    }

    /** Stands in for a request that never comes back. */
    private class NeverReturns(private val observed: FakeRecorder) : AiTransport {
        override suspend fun complete(request: AiRequest): AiResponse {
            observed.attemptWasRecordedBeforeSend = observed.attempts.isNotEmpty()
            error("request never returned")
        }
    }
}
