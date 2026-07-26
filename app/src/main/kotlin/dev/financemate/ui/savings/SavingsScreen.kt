package dev.financemate.ui.savings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import dev.financemate.feature.insight.subscription.DuplicateSubscriptionFinding
import dev.financemate.feature.insight.subscription.PriceChangeFinding
import dev.financemate.feature.insight.subscription.Subscription
import dev.financemate.ui.formatted
import dev.financemate.ui.formattedRounded
import dev.financemate.ui.theme.FinanceMateTheme
import java.time.format.DateTimeFormatter

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

@Composable
public fun SavingsScreen(
    state: SavingsUiState,
    onTagMerchant: (Subscription) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        SavingsUiState.Loading -> CentredMessage("Analysing your transactions…", showSpinner = true)

        is SavingsUiState.Failed -> CentredMessage(state.message)

        is SavingsUiState.Loaded -> if (!state.hasAnyData) {
            CentredMessage(
                "No recurring payments found yet.\n\n" +
                    "Import a bank statement and FinanceMate will look for " +
                    "subscriptions, duplicates, and price rises — all on this device.",
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
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            HeadlineCard(
                annualTotal = report.totalAnnualCost.formattedRounded(),
                monthlyTotal = report.totalMonthlyCost.formatted(),
                subscriptionCount = report.activeSubscriptions.size,
                identifiedSavings = report.identifiedAnnualSavings
                    .takeIf { !it.isZero }
                    ?.formattedRounded(),
            )
        }

        if (report.duplicates.isNotEmpty()) {
            item { SectionHeading("Overlapping subscriptions") }
            items(report.duplicates) { DuplicateCard(it) }
        }

        if (report.priceIncreases.isNotEmpty()) {
            item { SectionHeading("Price rises") }
            items(report.priceIncreases) { PriceRiseCard(it) }
        }

        if (state.untaggedMerchantCount > 0) {
            item {
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Help find more savings", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${state.untaggedMerchantCount} recurring payments aren't " +
                                "recognised yet. Tap one below to say what it is, and " +
                                "FinanceMate can spot overlaps with your other subscriptions.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }

        item { SectionHeading("All subscriptions") }
        items(report.activeSubscriptions) { subscription ->
            SubscriptionRow(subscription, onClick = { onTagMerchant(subscription) })
        }

        if (report.cancelledSubscriptions.isNotEmpty()) {
            item { SectionHeading("No longer charging") }
            items(report.cancelledSubscriptions) { subscription ->
                SubscriptionRow(subscription, onClick = { onTagMerchant(subscription) }, dimmed = true)
            }
        }
    }
}

@Composable
private fun HeadlineCard(
    annualTotal: String,
    monthlyTotal: String,
    subscriptionCount: Int,
    identifiedSavings: String?,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Recurring payments", style = MaterialTheme.typography.labelLarge)
            Text(
                annualTotal,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "a year — $monthlyTotal a month across $subscriptionCount " +
                    if (subscriptionCount == 1) "subscription" else "subscriptions",
                style = MaterialTheme.typography.bodyMedium,
            )
            identifiedSavings?.let {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(
                    "$it a year in overlapping subscriptions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun DuplicateCard(finding: DuplicateSubscriptionFinding) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(finding.serviceClass.displayName, style = MaterialTheme.typography.titleMedium)
                Text(
                    "Save ${finding.potentialAnnualSaving.formattedRounded()}/yr",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                "You're paying for ${finding.subscriptions.size} of these.",
                style = MaterialTheme.typography.bodyMedium,
            )
            finding.subscriptions.forEach { subscription ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(subscription.merchantKey.value, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${subscription.currentAmount.formatted()} ${subscription.cadence.displayName.lowercase()}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Text(
                "Estimate assumes keeping the cheapest.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun PriceRiseCard(finding: PriceChangeFinding) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(finding.merchantKey.value, style = MaterialTheme.typography.titleMedium)
            Text(
                "${finding.previousAmount.formatted()} → ${finding.currentAmount.formatted()}" +
                    (finding.changedOn?.let { " on ${it.format(DATE_FORMAT)}" } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "${finding.annualImpact.formatted()} more a year",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun SubscriptionRow(
    subscription: Subscription,
    onClick: () -> Unit,
    dimmed: Boolean = false,
) {
    Card(onClick = onClick) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    subscription.merchantKey.value,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Text(subscription.currentAmount.formatted(), style = MaterialTheme.typography.titleSmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    subscription.cadence.displayName,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "· ${subscription.annualCost.formattedRounded()}/yr",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (!dimmed) {
                    Text(
                        "· next ${subscription.nextExpected.format(DATE_FORMAT)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = onClick,
                    label = { Text(subscription.serviceClass?.displayName ?: "Tap to categorise") },
                )
                // Confidence is shown rather than hidden. A pattern the app is
                // unsure about is still worth surfacing, but the user deserves to
                // know how much weight to put on it.
                if (subscription.confidence < 0.75) {
                    AssistChip(onClick = onClick, label = { Text("Unsure") })
                }
            }
        }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun CentredMessage(message: String, showSpinner: Boolean = false) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (showSpinner) CircularProgressIndicator()
            Text(
                message,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun EmptyPreview() {
    FinanceMateTheme(dynamicColor = false) {
        SavingsScreen(
            state = SavingsUiState.Loaded(
                report = dev.financemate.feature.insight.subscription.SubscriptionReport(
                    subscriptions = emptyList(),
                    duplicates = emptyList(),
                    priceIncreases = emptyList(),
                    totalAnnualCost = dev.financemate.core.money.Money.zero(
                        dev.financemate.core.money.CurrencyCode.USD,
                    ),
                    totalMonthlyCost = dev.financemate.core.money.Money.zero(
                        dev.financemate.core.money.CurrencyCode.USD,
                    ),
                ),
                untaggedMerchantCount = 0,
            ),
            onTagMerchant = {},
        )
    }
}
