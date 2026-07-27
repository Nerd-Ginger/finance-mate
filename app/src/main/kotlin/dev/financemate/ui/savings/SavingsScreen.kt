package dev.financemate.ui.savings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.financemate.core.money.CurrencyCode
import dev.financemate.core.money.Money
import dev.financemate.feature.insight.subscription.DuplicateSubscriptionFinding
import dev.financemate.feature.insight.subscription.PriceChangeFinding
import dev.financemate.feature.insight.subscription.Subscription
import dev.financemate.feature.insight.subscription.SubscriptionReport
import dev.financemate.ui.components.ConfidenceChip
import dev.financemate.ui.components.DetailRow
import dev.financemate.ui.components.FindingCard
import dev.financemate.ui.components.FindingConfidence
import dev.financemate.ui.components.SectionLabel
import dev.financemate.ui.formatted
import dev.financemate.ui.formattedRounded
import dev.financemate.ui.theme.FinanceMate
import dev.financemate.ui.theme.FinanceMateTheme
import java.time.format.DateTimeFormatter

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

@Composable
fun SavingsScreen(
    state: SavingsUiState,
    onTagMerchant: (Subscription) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        SavingsUiState.Loading -> CentredMessage("Reading your transactions…", showSpinner = true)

        is SavingsUiState.Failed -> CentredMessage(state.message)

        is SavingsUiState.Loaded -> if (!state.hasAnyData) {
            CentredMessage(
                "Nothing recurring yet.\n\n" +
                    "Import a statement and FinanceMate will look for subscriptions, " +
                    "overlaps and price rises — all on this phone.",
            )
        } else {
            LoadedSavings(state, onTagMerchant, modifier)
        }
    }
}

@Composable
private fun LoadedSavings(
    state: SavingsUiState.Loaded,
    onTagMerchant: (Subscription) -> Unit,
    modifier: Modifier = Modifier,
) {
    val report = state.report

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Headline(report) }

        if (report.duplicates.isNotEmpty()) {
            item { SectionLabel("Overlapping") }
            items(report.duplicates) { DuplicateCard(it) }
        }

        if (report.priceIncreases.isNotEmpty()) {
            item { SectionLabel("Price rises") }
            items(report.priceIncreases) { PriceRiseCard(it) }
        }

        if (state.untaggedMerchantCount > 0) {
            item { UntaggedPrompt(state.untaggedMerchantCount) }
        }

        item { SectionLabel("All subscriptions") }
        items(report.activeSubscriptions) { SubscriptionRow(it, onClick = { onTagMerchant(it) }) }

        if (report.cancelledSubscriptions.isNotEmpty()) {
            item { SectionLabel("No longer charging") }
            items(report.cancelledSubscriptions) {
                SubscriptionRow(it, onClick = { onTagMerchant(it) }, dimmed = true)
            }
        }
    }
}

/**
 * The number the app leads with.
 *
 * Framed as **current annualised recurring spend**, with the overlap called out
 * separately and explicitly labelled as not yet saved. A headline reading
 * "you've saved $146" would be a lie until the user actually cancels something,
 * and getting caught in that lie once would cost every other number in the app
 * its credibility.
 */
@Composable
private fun Headline(report: SubscriptionReport) {
    val colours = FinanceMate.colours
    val count = report.activeSubscriptions.size
    val overlap = report.identifiedAnnualSavings

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(16.dp))
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                RoundedCornerShape(16.dp),
            )
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SectionLabel("Recurring, per year")
        Text(
            text = report.totalAnnualCost.formattedRounded(),
            style = MaterialTheme.typography.displaySmall.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "${report.totalMonthlyCost.formatted()} a month · " +
                "$count ${if (count == 1) "subscription" else "subscriptions"}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (!overlap.isZero) {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            Text(
                text = overlap.formattedRounded(),
                style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"),
                color = colours.foundMoney,
            )
            Text(
                text = "a year sitting in overlapping subscriptions. Not saved yet — " +
                    "it becomes real when you cancel something.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DuplicateCard(finding: DuplicateSubscriptionFinding) {
    val colours = FinanceMate.colours
    val count = finding.subscriptions.size

    FindingCard(confidence = FindingConfidence.CERTAIN) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = "You're paying for $count ${finding.serviceClass.displayName.lowercase()} services",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f).padding(end = 12.dp),
            )
            ConfidenceChip(FindingConfidence.CERTAIN, 1.0)
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            finding.subscriptions.forEach { subscription ->
                DetailRow(
                    label = "${subscription.merchantKey.value} · " +
                        subscription.cadence.displayName.lowercase(),
                    value = "−${subscription.currentAmount.formatted()}",
                    labelColour = colours.foundMoneyText,
                    valueColour = colours.foundMoneyText,
                )
            }
        }

        HorizontalDivider(color = colours.foundMoneyBorder.copy(alpha = 0.6f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = "Assumes keeping the cheapest",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(end = 12.dp),
            )
            Text(
                text = "${finding.potentialAnnualSaving.formattedRounded()}/yr",
                style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"),
                color = colours.foundMoney,
            )
        }
    }
}

@Composable
private fun PriceRiseCard(finding: PriceChangeFinding) {
    val colours = FinanceMate.colours

    FindingCard(confidence = FindingConfidence.CERTAIN) {
        Text(
            text = finding.merchantKey.value,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = buildString {
                append(finding.previousAmount.formatted())
                append("  →  ")
                append(finding.currentAmount.formatted())
                finding.changedOn?.let { append(" on ${it.format(DATE_FORMAT)}") }
            },
            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
            color = colours.foundMoneyText,
        )
        Text(
            text = "${finding.annualImpact.formatted()} more a year",
            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
            color = colours.foundMoney,
        )
    }
}

@Composable
private fun UntaggedPrompt(count: Int) {
    FindingCard(confidence = FindingConfidence.UNCERTAIN) {
        Text(
            text = "$count recurring payments aren't recognised",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Tap one below to say what it is. FinanceMate can then spot " +
                "overlaps with your other subscriptions.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SubscriptionRow(
    subscription: Subscription,
    onClick: () -> Unit,
    dimmed: Boolean = false,
) {
    val colours = FinanceMate.colours
    val confidence = FindingConfidence.from(subscription.confidence)
    val shape = RoundedCornerShape(12.dp)

    val nameColour = if (dimmed) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, shape)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shape)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = subscription.merchantKey.value,
                style = MaterialTheme.typography.titleSmall,
                color = nameColour,
                modifier = Modifier.weight(1f).padding(end = 12.dp),
            )
            Text(
                // Ordinary spending stays neutral. The minus sign carries the
                // meaning; colouring it would make every subscription look like
                // a problem, and most of them are things the user wants.
                text = "−${subscription.currentAmount.formatted()}",
                style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
                color = if (dimmed) MaterialTheme.colorScheme.onSurfaceVariant else colours.moneyOut,
            )
        }

        Text(
            text = buildString {
                append(subscription.cadence.displayName)
                append(" · ")
                append("${subscription.annualCost.formattedRounded()}/yr")
                if (!dimmed) append(" · next ${subscription.nextExpected.format(DATE_FORMAT)}")
            },
            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = subscription.serviceClass?.displayName ?: "Tap to categorise",
                style = MaterialTheme.typography.bodySmall,
                color = if (subscription.serviceClass == null) {
                    colours.foundMoney
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier
                    .border(
                        BorderStroke(
                            1.dp,
                            if (subscription.serviceClass == null) {
                                colours.foundMoneyBorder
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                        ),
                        RoundedCornerShape(3.dp),
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
            if (confidence != FindingConfidence.CERTAIN) {
                ConfidenceChip(confidence, subscription.confidence)
            }
        }
    }
}

@Composable
private fun CentredMessage(message: String, showSpinner: Boolean = false) {
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (showSpinner) {
                CircularProgressIndicator(color = FinanceMate.colours.foundMoney)
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0908)
@Composable
private fun EmptyPreview() {
    FinanceMateTheme {
        SavingsScreen(
            state = SavingsUiState.Loaded(
                report = SubscriptionReport(
                    subscriptions = emptyList(),
                    duplicates = emptyList(),
                    priceIncreases = emptyList(),
                    totalAnnualCost = Money.zero(CurrencyCode.USD),
                    totalMonthlyCost = Money.zero(CurrencyCode.USD),
                ),
                untaggedMerchantCount = 0,
            ),
            onTagMerchant = {},
        )
    }
}
