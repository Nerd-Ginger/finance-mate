package dev.financemate.core.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The user's own opinion about what a merchant is.
 *
 * [serviceClass] being null is meaningful, not missing: it records "this is not
 * a subscription service", which suppresses the built-in catalogue. Without a
 * way to say that, a merchant the catalogue classified wrongly would reappear
 * as a suggestion after every import, and there would be no way to dismiss it.
 *
 * A row exists only when the user has expressed an opinion, so the absence of a
 * row and an explicit dismissal remain distinguishable.
 */
@Entity(tableName = "merchant_classifications")
public data class MerchantClassificationEntity(
    @PrimaryKey public val merchantKey: String,
    /** ServiceClass name, or null meaning "not a subscription service". */
    public val serviceClass: String?,
    /** Optional friendlier name than the normalised key. */
    public val displayNameOverride: String? = null,
    public val updatedAtEpochMillis: Long,
)
