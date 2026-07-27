package dev.financemate.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import dev.financemate.ai.egress.EgressDisclosure
import dev.financemate.ai.egress.EgressDisclosureItem
import dev.financemate.ai.egress.EgressHandling
import dev.financemate.ui.theme.EgressStyle
import dev.financemate.ui.theme.FinanceMate
import dev.financemate.ui.theme.SectionLabelStyle

/**
 * What leaves this device, and the log that proves it.
 *
 * This screen is offered *before* the first file rather than buried in settings,
 * because the moment a user is deciding whether to hand over a bank statement is
 * the moment the question actually matters to them. Afterwards it is reassurance;
 * beforehand it is evidence.
 *
 * Everything network-related is mono on near-black throughout the app, so those
 * surfaces are recognisable on sight without reading a word.
 */
@Composable
public fun EgressProofScreen(
    state: EgressProofUiState,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.clickable(onClick = onBack),
            )
            Text(
                text = "What leaves this device",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp)
                .padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text(
                // Deliberately weaker than "generated from the code". It is a
                // declaration kept beside the transport, reviewed with it - and
                // claiming codegen we do not have would be exactly the sort of
                // unverifiable assertion this screen exists to replace.
                text = "Nothing, unless you switch on an AI feature. This list is " +
                    "declared inside the one module that is allowed to use the " +
                    "network, next to the code that would do it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            DisclosureTable(items = EgressDisclosure.items)

            EgressLogPanel(state = state)

            EncryptionNote()
        }

        Column(
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
        ) {
            PrimaryAction(label = "Continue", onClick = onContinue)
        }
    }
}

/**
 * The NEVER/OPT-IN table.
 *
 * Rows come from [EgressDisclosure] rather than from strings here, so the list
 * cannot drift away from what the network module actually declares.
 */
@Composable
private fun DisclosureTable(items: List<EgressDisclosureItem>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(12.dp),
            ),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        items.forEach { item ->
            val optIn = item.handling == EgressHandling.OPT_IN
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        // The one row that can ever carry data is a step lighter
                        // than the three that cannot. The difference is small on
                        // purpose: it should read as "this one is different",
                        // not as a warning.
                        if (optIn) {
                            MaterialTheme.colorScheme.surfaceContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        },
                    )
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = item.what,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.widthIn(max = 230.dp),
                )
                HandlingBadge(handling = item.handling)
            }
        }
    }
}

/**
 * NEVER is quiet; OPT-IN is orange.
 *
 * Backwards from the usual instinct, and correct: three greyed NEVERs and one
 * highlighted exception makes the exception findable. Shouting NEVER three times
 * would bury the only row worth reading twice.
 */
@Composable
private fun HandlingBadge(handling: EgressHandling) {
    when (handling) {
        EgressHandling.NEVER -> Text(
            text = "NEVER",
            style = SectionLabelStyle.copy(letterSpacing = 0.06.em),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        EgressHandling.OPT_IN -> Text(
            text = "OPT-IN",
            style = SectionLabelStyle.copy(letterSpacing = 0.06.em),
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .background(FinanceMate.colours.foundMoney, RoundedCornerShape(3.dp))
                .padding(horizontal = 8.dp, vertical = 5.dp),
        )
    }
}

/**
 * The log itself.
 *
 * On a device that has never enabled AI this reads "0 requests since install",
 * and that number is counted from the database rather than written here. The
 * whole screen is worthless if this line is a constant.
 */
@Composable
private fun EgressLogPanel(state: EgressProofUiState) {
    val colours = FinanceMate.colours

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "EGRESS LOG",
            style = SectionLabelStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colours.egressBackground, RoundedCornerShape(10.dp))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(10.dp),
                )
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = state.summaryLine,
                style = EgressStyle.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Every request the app ever makes is recorded here with its " +
                    "exact payload. You can read it any time, including before you " +
                    "decide to trust us.",
                style = EgressStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The ledger's encryption, stated once, in the one place someone would look. */
@Composable
private fun EncryptionNote() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(FinanceMate.colours.moneyIn, CircleShape),
        )
        Text(
            text = "Ledger is encrypted with a key generated on this phone and held " +
                "in its secure hardware.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
