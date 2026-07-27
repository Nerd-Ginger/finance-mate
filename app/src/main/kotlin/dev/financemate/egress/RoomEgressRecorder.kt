package dev.financemate.egress

import dev.financemate.ai.egress.EgressAttempt
import dev.financemate.ai.egress.EgressOutcome
import dev.financemate.ai.egress.EgressRecorder
import dev.financemate.core.data.dao.EgressLogDao
import dev.financemate.core.data.entity.EgressLogEntity

/**
 * Writes `:ai`'s egress records into the encrypted ledger.
 *
 * The adapter lives in `:app` rather than in either module it joins. `:ai` must
 * not know about storage — it is the one module allowed to open a socket, and
 * keeping its dependencies minimal is what makes that claim easy to audit. And
 * `:core:data` must not depend on `:ai`, because then every module that reads
 * the ledger would transitively gain the ability to construct a transport.
 *
 * That leaves the composition root, which is where an adapter between a port and
 * an implementation belongs anyway.
 */
public class RoomEgressRecorder(
    private val dao: EgressLogDao,
) : EgressRecorder {

    override suspend fun recordAttempt(attempt: EgressAttempt): Long = dao.insert(
        EgressLogEntity(
            occurredAtEpochMillis = attempt.occurredAt.toEpochMilli(),
            featureId = attempt.featureId,
            endpoint = attempt.endpoint,
            modelId = attempt.modelId,
            payload = attempt.payload,
            payloadBytes = attempt.payloadBytes,
        ),
    )

    override suspend fun recordOutcome(id: Long, outcome: EgressOutcome) {
        when (outcome) {
            is EgressOutcome.Completed -> dao.completeEntry(
                id = id,
                outcome = EgressLogEntity.OUTCOME_COMPLETED,
                responseBytes = outcome.responseBytes,
                inputTokens = outcome.inputTokens,
                outputTokens = outcome.outputTokens,
                failureReason = null,
            )

            is EgressOutcome.Failed -> dao.completeEntry(
                id = id,
                outcome = EgressLogEntity.OUTCOME_FAILED,
                responseBytes = null,
                inputTokens = null,
                outputTokens = null,
                failureReason = outcome.reason,
            )
        }
    }
}
