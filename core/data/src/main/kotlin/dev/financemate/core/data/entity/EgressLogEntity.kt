package dev.financemate.core.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One request that left, or tried to leave, the device.
 *
 * Rows are written **before** the request is sent and updated when it finishes,
 * so a request that hung or was interrupted still leaves a row with a null
 * [outcome]. That is the point: a log that only recorded completed requests
 * would be a log of the boring ones.
 *
 * Nothing here is ever deleted. The app's claim is about its whole history, and
 * a log the app can prune is a log the app can edit.
 *
 * ## Why it lives in the encrypted ledger
 *
 * [payload] holds exactly what was sent, verbatim, so the user can audit the
 * real bytes rather than our description of them. Payloads are redacted before
 * they are sent, but a redacted merchant name is still the user's data and does
 * not deserve weaker storage than the transactions it came from.
 */
@Entity(
    tableName = "egress_log",
    indices = [Index(value = ["occurredAtEpochMillis"])],
)
public data class EgressLogEntity(
    @PrimaryKey(autoGenerate = true) public val id: Long = 0,
    public val occurredAtEpochMillis: Long,
    /** Which feature raised the call, so entries can be attributed. */
    public val featureId: String,
    public val endpoint: String,
    public val modelId: String,
    /** Exactly what was sent. Not a summary. */
    public val payload: String,
    public val payloadBytes: Int,
    /**
     * `null` until the request finishes, then [OUTCOME_COMPLETED] or
     * [OUTCOME_FAILED]. Null on an old row means the request never came back.
     */
    public val outcome: String? = null,
    public val responseBytes: Int? = null,
    public val inputTokens: Long? = null,
    public val outputTokens: Long? = null,
    /** Exception class name on failure. Never a message, never a body. */
    public val failureReason: String? = null,
) {
    public companion object {
        public const val OUTCOME_COMPLETED: String = "COMPLETED"
        public const val OUTCOME_FAILED: String = "FAILED"
    }
}
