package dev.financemate.egress

import dev.financemate.core.data.dao.EgressLogDao
import dev.financemate.core.data.entity.EgressLogEntity
import java.time.Instant

/**
 * Read access to the egress log, for the screen that shows it to the user.
 *
 * Read-only by construction. The write path goes through [RoomEgressRecorder]
 * and nothing else, so a feature cannot append a flattering entry, and nothing
 * anywhere can remove one.
 */
public class EgressLogRepository(
    private val dao: EgressLogDao,
) {

    /** How many requests the app has ever made. Zero until AI is switched on. */
    public suspend fun requestCount(): Int = dao.count()

    public suspend fun recent(limit: Int = DEFAULT_LIMIT): List<EgressLogEntry> =
        dao.recent(limit).map { it.toEntry() }

    public companion object {
        public const val DEFAULT_LIMIT: Int = 50
    }
}

/** One entry as the UI reads it. */
public data class EgressLogEntry(
    val id: Long,
    val occurredAt: Instant,
    val featureId: String,
    val endpoint: String,
    val modelId: String,
    val payload: String,
    val payloadBytes: Int,
    val status: EgressStatus,
)

public enum class EgressStatus {
    /**
     * Written before the request was sent, never completed. Either it is in
     * flight right now, or the app was killed mid-request. Shown as-is rather
     * than hidden — an unexplained entry is more honest than a missing one.
     */
    UNFINISHED,
    COMPLETED,
    FAILED,
}

private fun EgressLogEntity.toEntry() = EgressLogEntry(
    id = id,
    occurredAt = Instant.ofEpochMilli(occurredAtEpochMillis),
    featureId = featureId,
    endpoint = endpoint,
    modelId = modelId,
    payload = payload,
    payloadBytes = payloadBytes,
    status = when (outcome) {
        EgressLogEntity.OUTCOME_COMPLETED -> EgressStatus.COMPLETED
        EgressLogEntity.OUTCOME_FAILED -> EgressStatus.FAILED
        else -> EgressStatus.UNFINISHED
    },
)
