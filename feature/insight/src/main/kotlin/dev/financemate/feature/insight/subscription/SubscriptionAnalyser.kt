package dev.financemate.feature.insight.subscription

import dev.financemate.core.model.MerchantKey
import dev.financemate.core.money.Money
import dev.financemate.core.money.sum
import dev.financemate.feature.insight.recurring.Cadence
import dev.financemate.feature.insight.recurring.RecurringSeries
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import kotlin.math.abs

/**
 * Turns recurring charges into things the user can act on.
 *
 * Every finding here is arithmetic over the local ledger. None of it needs a
 * language model, and none of it would be improved by one — a price rise either
 * happened or it did not, and an LLM asked the same question would be slower,
 * cost money, and occasionally be wrong.
 */
public class SubscriptionAnalyser(
    /**
     * Smallest change treated as a real price rise, as a fraction.
     *
     * Below this, a change is more likely to be sales-tax drift or an FX
     * conversion than a decision by the vendor, and reporting it would train the
     * user to ignore the alerts that matter.
     */
    private val priceChangeThreshold: BigDecimal = BigDecimal("0.02"),

    /** Series below this confidence are not reported as subscriptions. */
    private val minimumConfidence: Double = 0.55,
) {

    public fun analyse(
        series: List<RecurringSeries>,
        asOf: LocalDate,
    ): SubscriptionReport {
        val subscriptions = series
            .filter { it.confidence >= minimumConfidence }
            .map { it.toSubscription(asOf) }

        val active = subscriptions.filter { it.isActive }

        return SubscriptionReport(
            subscriptions = subscriptions,
            duplicates = findDuplicates(active),
            priceIncreases = subscriptions.mapNotNull { it.detectPriceChange() }
                .filter { it.isIncrease },
            totalAnnualCost = active.map { it.annualCost }.sumOrZero(),
            totalMonthlyCost = active.map { it.monthlyEquivalent }.sumOrZero(),
        )
    }

    private fun RecurringSeries.toSubscription(asOf: LocalDate): Subscription = Subscription(
        merchantKey = merchantKey,
        serviceClass = ServiceCatalogue.classify(merchantKey),
        cadence = cadence,
        currentAmount = typicalAmount.abs(),
        annualCost = annualisedCost.abs(),
        monthlyEquivalent = monthlyEquivalent.abs(),
        firstSeen = firstSeen,
        lastCharged = lastSeen,
        nextExpected = nextExpected,
        isActive = isActive(asOf),
        confidence = confidence,
        amountHistory = amountHistory.map { it.abs() },
        chargeDates = occurrences.map { it.postedDate },
    )

    /**
     * Finds several active subscriptions doing the same job.
     *
     * Only classes marked [ServiceClass.usuallyRedundant] are reported as
     * something to fix. Two video services is a normal choice, and flagging it
     * would make the whole feature feel like nagging.
     */
    private fun findDuplicates(active: List<Subscription>): List<DuplicateSubscriptionFinding> =
        active
            .filter { it.serviceClass != null }
            .groupBy { it.serviceClass!! }
            .filter { (serviceClass, subs) -> subs.size > 1 && serviceClass.usuallyRedundant }
            .map { (serviceClass, subs) ->
                val ordered = subs.sortedByDescending { it.annualCost.minorUnits }
                DuplicateSubscriptionFinding(
                    serviceClass = serviceClass,
                    subscriptions = ordered,
                    // Keeping the cheapest is the conservative recommendation:
                    // it never assumes which one the user values, only which
                    // costs least.
                    potentialAnnualSaving = ordered.dropLast(1)
                        .map { it.annualCost }
                        .sumOrZero(),
                )
            }
            .sortedByDescending { it.potentialAnnualSaving.minorUnits }

    /**
     * Finds a step change in a subscription's price.
     *
     * Compares runs of similar amounts rather than first-versus-last, so a
     * one-off charge (an annual add-on, a partial month) does not read as a
     * permanent rise. Only a change that *persisted* counts.
     */
    private fun Subscription.detectPriceChange(): PriceChangeFinding? {
        if (amountHistory.size < 3) return null

        val runs = amountHistory.groupIntoRuns(priceChangeThreshold)
        if (runs.size < 2) return null

        val previous = runs[runs.size - 2]
        val current = runs.last()

        // A single charge at a new price might be a blip; require it to have
        // stuck, unless it is the only evidence we have and clearly a step.
        if (current.count < 2 && runs.size > 2) return null

        val delta = current.representative - previous.representative
        if (delta.isZero) return null

        val relativeChange = BigDecimal(abs(delta.minorUnits))
            .divide(BigDecimal(previous.representative.minorUnits.coerceAtLeast(1)), 4, RoundingMode.HALF_UP)
        if (relativeChange < priceChangeThreshold) return null

        val perYear = BigDecimal(cadence.occurrencesPerYear).setScale(4, RoundingMode.HALF_UP)

        return PriceChangeFinding(
            merchantKey = merchantKey,
            previousAmount = previous.representative,
            currentAmount = current.representative,
            changedOn = chargeDates.getOrNull(amountHistory.size - current.count),
            annualImpact = delta.scaleBy(perYear),
            percentChange = relativeChange.multiply(BigDecimal(100)),
        )
    }

    /**
     * Groups consecutive amounts that are within [threshold] of each other.
     *
     * This is what makes the price-change detector robust: a subscription that
     * goes 9.99, 9.99, 9.99, 12.99, 12.99 has two runs and an obvious step,
     * while one that goes 9.99, 9.99, 40.00, 9.99, 9.99 has a blip in the middle
     * and no lasting change to report.
     */
    private fun List<Money>.groupIntoRuns(threshold: BigDecimal): List<AmountRun> {
        if (isEmpty()) return emptyList()
        val runs = mutableListOf<AmountRun>()
        var currentValue = first()
        var count = 0

        for (amount in this) {
            val base = currentValue.minorUnits.coerceAtLeast(1)
            val difference = BigDecimal(abs(amount.minorUnits - currentValue.minorUnits))
                .divide(BigDecimal(base), 4, RoundingMode.HALF_UP)

            if (difference <= threshold) {
                count++
            } else {
                runs.add(AmountRun(currentValue, count))
                currentValue = amount
                count = 1
            }
        }
        runs.add(AmountRun(currentValue, count))
        return runs
    }

    private data class AmountRun(val representative: Money, val count: Int)
}

/** Sums money, tolerating an empty list without needing a currency up front. */
private fun List<Money>.sumOrZero(): Money =
    firstOrNull()?.let { this.sum(it.currency) } ?: Money.zero(dev.financemate.core.money.CurrencyCode.USD)

public data class Subscription(
    val merchantKey: MerchantKey,
    val serviceClass: ServiceClass?,
    val cadence: Cadence,
    /** Positive. Costs are presented as magnitudes, not as negative ledger amounts. */
    val currentAmount: Money,
    val annualCost: Money,
    val monthlyEquivalent: Money,
    val firstSeen: LocalDate,
    val lastCharged: LocalDate,
    val nextExpected: LocalDate,
    val isActive: Boolean,
    val confidence: Double,
    val amountHistory: List<Money>,
    val chargeDates: List<LocalDate>,
)

public data class DuplicateSubscriptionFinding(
    val serviceClass: ServiceClass,
    /** Most expensive first. */
    val subscriptions: List<Subscription>,
    /** Saving from keeping only the cheapest. */
    val potentialAnnualSaving: Money,
)

public data class PriceChangeFinding(
    val merchantKey: MerchantKey,
    val previousAmount: Money,
    val currentAmount: Money,
    val changedOn: LocalDate?,
    /** Positive when the price rose. */
    val annualImpact: Money,
    val percentChange: BigDecimal,
) {
    val isIncrease: Boolean get() = currentAmount > previousAmount
}

public data class SubscriptionReport(
    val subscriptions: List<Subscription>,
    val duplicates: List<DuplicateSubscriptionFinding>,
    val priceIncreases: List<PriceChangeFinding>,
    val totalAnnualCost: Money,
    val totalMonthlyCost: Money,
) {
    val activeSubscriptions: List<Subscription> get() = subscriptions.filter { it.isActive }

    /**
     * Subscriptions no longer being charged.
     *
     * Worth showing rather than hiding: it is how a user confirms a cancellation
     * actually took effect.
     */
    val cancelledSubscriptions: List<Subscription> get() = subscriptions.filterNot { it.isActive }

    /** Everything the duplicate findings say could be saved in a year. */
    val identifiedAnnualSavings: Money
        get() = duplicates.map { it.potentialAnnualSaving }.sumOrZero()
}
