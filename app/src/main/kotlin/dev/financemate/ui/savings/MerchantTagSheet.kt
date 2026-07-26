package dev.financemate.ui.savings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.financemate.feature.insight.subscription.ServiceClass
import dev.financemate.feature.insight.subscription.Subscription
import dev.financemate.ui.formatted

/**
 * Lets the user say what a merchant actually is.
 *
 * This is what closes the gap the built-in catalogue cannot: it only knows the
 * services it happens to list, so regional gyms, niche tools, and anything
 * recent are invisible to it. The user can see their own statement.
 *
 * "Not a subscription" is a first-class option, not an omission. Without it, a
 * merchant the catalogue guessed wrong would keep reappearing as a suggestion
 * after every import with no way to dismiss it.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
public fun MerchantTagSheet(
    subscription: Subscription,
    onSelect: (ServiceClass?) -> Unit,
    onClearTag: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(subscription.merchantKey.value, style = MaterialTheme.typography.headlineSmall)
            Text(
                "${subscription.currentAmount.formatted()} " +
                    subscription.cadence.displayName.lowercase() +
                    " · ${subscription.annualCost.formatted()} a year",
                style = MaterialTheme.typography.bodyMedium,
            )

            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            Text("What kind of service is this?", style = MaterialTheme.typography.titleMedium)
            Text(
                "Telling FinanceMate lets it spot overlaps with your other " +
                    "subscriptions. This is stored on your device.",
                style = MaterialTheme.typography.bodySmall,
            )

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ServiceClass.entries.forEach { serviceClass ->
                    FilterChip(
                        selected = subscription.serviceClass == serviceClass,
                        onClick = { onSelect(serviceClass) },
                        label = { Text(serviceClass.displayName) },
                    )
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            TextButton(onClick = { onSelect(null) }) {
                Text("Not a subscription — stop suggesting this")
            }
            TextButton(onClick = onClearTag) {
                Text("Reset to the default")
            }
        }
    }
}
