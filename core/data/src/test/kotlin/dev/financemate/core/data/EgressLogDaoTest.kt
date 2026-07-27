package dev.financemate.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.financemate.core.data.entity.EgressLogEntity
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EgressLogDaoTest {

    private lateinit var database: FinanceMateDatabase
    private val dao get() = database.egressLogDao()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, FinanceMateDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun startsEmpty() = runTest {
        // The proof screen says "0 requests since install" on a fresh device.
        // This is the assertion behind that sentence.
        dao.count() shouldBe 0
        dao.recent(limit = 10) shouldBe emptyList()
    }

    @Test
    fun anUnfinishedAttemptIsStillCounted() = runTest {
        dao.insert(attempt(occurredAt = 1_000L))

        val entry = dao.recent(limit = 10).single()
        entry.outcome.shouldBeNull()
        // An interrupted request is visible, which is the entire reason the row
        // is written before the request is sent.
        dao.count() shouldBe 1
    }

    @Test
    fun completingAnEntryFillsInTheOutcome() = runTest {
        val id = dao.insert(attempt(occurredAt = 1_000L))

        dao.completeEntry(
            id = id,
            outcome = EgressLogEntity.OUTCOME_COMPLETED,
            responseBytes = 512,
            inputTokens = 900,
            outputTokens = 120,
            failureReason = null,
        )

        val entry = dao.recent(limit = 10).single()
        entry.outcome shouldBe EgressLogEntity.OUTCOME_COMPLETED
        entry.responseBytes shouldBe 512
        entry.inputTokens shouldBe 900
        entry.outputTokens shouldBe 120
        entry.failureReason.shouldBeNull()
        // Completing must not disturb what was recorded before the send.
        entry.payload shouldBe "SPOTIFY, NETFLIX"
        entry.payloadBytes shouldBe 16
    }

    @Test
    fun aFailedEntryKeepsItsPayloadAndReason() = runTest {
        val id = dao.insert(attempt(occurredAt = 1_000L))

        dao.completeEntry(
            id = id,
            outcome = EgressLogEntity.OUTCOME_FAILED,
            responseBytes = null,
            inputTokens = null,
            outputTokens = null,
            failureReason = "AiTransportException",
        )

        val entry = dao.recent(limit = 10).single()
        entry.outcome shouldBe EgressLogEntity.OUTCOME_FAILED
        entry.failureReason shouldBe "AiTransportException"
        entry.payload shouldBe "SPOTIFY, NETFLIX"
    }

    @Test
    fun mostRecentFirst() = runTest {
        dao.insert(attempt(occurredAt = 1_000L, featureId = "oldest"))
        dao.insert(attempt(occurredAt = 3_000L, featureId = "newest"))
        dao.insert(attempt(occurredAt = 2_000L, featureId = "middle"))

        dao.recent(limit = 10).map { it.featureId }
            .shouldContainExactly("newest", "middle", "oldest")
    }

    @Test
    fun twoRequestsInTheSameMillisecondStillOrderByArrival() = runTest {
        // Plausible for a batched classification run, and a plain timestamp sort
        // would leave the order undefined.
        dao.insert(attempt(occurredAt = 1_000L, featureId = "first"))
        dao.insert(attempt(occurredAt = 1_000L, featureId = "second"))

        dao.recent(limit = 10).map { it.featureId }
            .shouldContainExactly("second", "first")
    }

    private fun attempt(
        occurredAt: Long,
        featureId: String = "classify-merchants",
    ) = EgressLogEntity(
        occurredAtEpochMillis = occurredAt,
        featureId = featureId,
        endpoint = "https://api.anthropic.com/v1/messages",
        modelId = "claude-opus-5",
        payload = "SPOTIFY, NETFLIX",
        payloadBytes = 16,
    )
}
