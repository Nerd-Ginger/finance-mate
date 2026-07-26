package dev.financemate.core.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.financemate.core.data.entity.MerchantClassificationEntity
import kotlinx.coroutines.flow.Flow

@Dao
public interface MerchantClassificationDao {

    @Upsert
    public suspend fun upsert(classification: MerchantClassificationEntity)

    @Query("SELECT * FROM merchant_classifications")
    public suspend fun all(): List<MerchantClassificationEntity>

    @Query("SELECT * FROM merchant_classifications")
    public fun observeAll(): Flow<List<MerchantClassificationEntity>>

    @Query("SELECT * FROM merchant_classifications WHERE merchantKey = :merchantKey")
    public suspend fun byMerchant(merchantKey: String): MerchantClassificationEntity?

    /** Clears the user's opinion, returning the merchant to catalogue defaults. */
    @Query("DELETE FROM merchant_classifications WHERE merchantKey = :merchantKey")
    public suspend fun clear(merchantKey: String)
}
