package dev.financemate.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import dev.financemate.core.data.entity.EgressLogEntity
import kotlinx.coroutines.flow.Flow

/**
 * Append-and-complete only.
 *
 * There is deliberately no delete and no general update. The log's value is that
 * the app cannot quietly edit its own history, and the cheapest way to keep that
 * true is to give the rest of the codebase no way to try.
 */
@Dao
public interface EgressLogDao {

    @Insert
    public suspend fun insert(entry: EgressLogEntity): Long

    /** Fills in how a request ended. The only permitted mutation. */
    @Query(
        """
        UPDATE egress_log
        SET outcome = :outcome,
            responseBytes = :responseBytes,
            inputTokens = :inputTokens,
            outputTokens = :outputTokens,
            failureReason = :failureReason
        WHERE id = :id
        """,
    )
    public suspend fun completeEntry(
        id: Long,
        outcome: String,
        responseBytes: Int?,
        inputTokens: Long?,
        outputTokens: Long?,
        failureReason: String?,
    )

    @Query("SELECT COUNT(*) FROM egress_log")
    public suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM egress_log")
    public fun observeCount(): Flow<Int>

    @Query("SELECT * FROM egress_log ORDER BY occurredAtEpochMillis DESC, id DESC LIMIT :limit")
    public suspend fun recent(limit: Int): List<EgressLogEntity>

    @Query("SELECT * FROM egress_log ORDER BY occurredAtEpochMillis DESC, id DESC LIMIT :limit")
    public fun observeRecent(limit: Int): Flow<List<EgressLogEntity>>
}
