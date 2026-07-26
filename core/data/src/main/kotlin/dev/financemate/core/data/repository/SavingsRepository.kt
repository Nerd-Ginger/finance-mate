package dev.financemate.core.data.repository

import dev.financemate.core.data.FinanceMateDatabase
import dev.financemate.core.data.entity.MerchantClassificationEntity
import dev.financemate.core.data.mapper.toDomain
import dev.financemate.core.model.MerchantKey
import dev.financemate.feature.insight.recurring.RecurringSeriesDetector
import dev.financemate.feature.insight.subscription.MerchantOverride
import dev.financemate.feature.insight.subscription.OverridingMerchantClassifier
import dev.financemate.feature.insight.subscription.ServiceClass
import dev.financemate.feature.insight.subscription.SubscriptionAnalyser
import dev.financemate.feature.insight.subscription.SubscriptionReport
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.LocalDate

/**
 * Runs the savings analysis over the stored ledger.
 *
 * Reads everything and analyses in memory rather than pushing the work into SQL.
 * Recurring detection needs each merchant's full history to measure the gaps
 * between charges, so there is no partial query that would help — and a personal
 * ledger is small: even a decade of heavy spending is a few tens of thousands of
 * rows, which is nothing to sort and group.
 */
public class SavingsRepository(
    private val database: FinanceMateDatabase,
    private val detector: RecurringSeriesDetector = RecurringSeriesDetector(),
    private val clock: Clock = Clock.systemDefaultZone(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    public suspend fun analyse(): SubscriptionReport = withContext(ioDispatcher) {
        val transactions = database.transactionDao().allChronological().map { it.toDomain() }
        val overrides = loadOverrides()

        val analyser = SubscriptionAnalyser(
            classifier = OverridingMerchantClassifier(overrides),
        )
        analyser.analyse(detector.detect(transactions), LocalDate.now(clock))
    }

    /**
     * Merchants that recur but have no service class, so the user can tag them.
     *
     * These are the gap the built-in catalogue cannot close — regional gyms,
     * niche tools, anything newer than the list. Presenting them ranked by cost
     * puts the ones worth tagging first.
     */
    public suspend fun untaggedRecurringMerchants(): List<UntaggedMerchant> =
        withContext(ioDispatcher) {
            val report = analyse()
            report.activeSubscriptions
                .filter { it.serviceClass == null }
                .map {
                    UntaggedMerchant(
                        merchantKey = it.merchantKey,
                        annualCostMinorUnits = it.annualCost.minorUnits,
                        currencyCode = it.annualCost.currency.code,
                    )
                }
                .sortedByDescending { it.annualCostMinorUnits }
        }

    public suspend fun setClassification(
        merchant: MerchantKey,
        serviceClass: ServiceClass?,
    ): Unit = withContext(ioDispatcher) {
        database.merchantClassificationDao().upsert(
            MerchantClassificationEntity(
                merchantKey = merchant.value,
                // null here is an explicit "not a subscription service", which is
                // why the row is written rather than deleted.
                serviceClass = serviceClass?.name,
                updatedAtEpochMillis = clock.millis(),
            ),
        )
    }

    /** Removes the user's opinion, restoring the built-in catalogue's answer. */
    public suspend fun clearClassification(merchant: MerchantKey): Unit = withContext(ioDispatcher) {
        database.merchantClassificationDao().clear(merchant.value)
    }

    private suspend fun loadOverrides(): Map<MerchantKey, MerchantOverride> =
        database.merchantClassificationDao().all().associate { row ->
            val override = row.serviceClass
                ?.let { name -> runCatching { ServiceClass.valueOf(name) }.getOrNull() }
                ?.let { MerchantOverride.Classified(it) }
                // A null class means dismissed. An unrecognised class name — from
                // a future version, or a rename — is treated the same way rather
                // than crashing; the user can re-tag it.
                ?: MerchantOverride.NotAService
            MerchantKey(row.merchantKey) to override
        }
}

public data class UntaggedMerchant(
    val merchantKey: MerchantKey,
    val annualCostMinorUnits: Long,
    val currencyCode: String,
)
