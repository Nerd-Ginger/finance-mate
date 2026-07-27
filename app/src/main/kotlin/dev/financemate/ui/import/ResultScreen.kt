package dev.financemate.ui.import

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.financemate.ui.onboarding.PrimaryAction
import dev.financemate.ui.theme.AmountStyle
import dev.financemate.ui.theme.FinanceMate
import dev.financemate.ui.theme.MonoMetaStyle
import dev.financemate.ui.theme.SectionLabelStyle

/**
 * What the import did.
 *
 * Counts, not congratulations. There is no tick, no "Success!", and no
 * celebration — the user did not come here to be congratulated for owning a bank
 * statement, they came to find out what is in it.
 *
 * Undo sits here at full size rather than hidden behind a menu, because the
 * moment somebody most wants it is the moment this screen tells them the file
 * was wrong: the wrong account, the wrong month, or signs the wrong way round.
 * Making them hunt for it then would be a small cruelty.
 */
@Composable
public fun ResultScreen(
    state: ImportUiState.Done,
    onSeeSavings: () -> Unit,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // Scrollable content above a fixed action area. Without this the undo
        // link slid under the navigation bar on a Pixel 5 — and undo is the one
        // control on this screen that must never be hard to reach.
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp),
        ) {
            Spacer(Modifier.height(48.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    // Deliberately larger than any heading in the type scale. This
                    // number is the answer to the question the user came with, and
                    // it is the one moment in the app where a figure is allowed to
                    // dominate a screen.
                    text = state.imported.toString(),
                    style = AmountStyle.copy(
                        fontSize = 56.sp,
                        lineHeight = 58.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.03).em,
                        textAlign = TextAlign.Start,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (state.imported == 1) {
                        "transaction added to ${state.accountName}"
                    } else {
                        "transactions added to ${state.accountName}"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.subtitle?.let {
                    Text(
                        text = it,
                        style = MonoMetaStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .padding(vertical = 28.dp)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            )

            WhileYouWereReading(state)

            Spacer(Modifier.height(24.dp))

            LocalWorkNote()

            Spacer(Modifier.height(24.dp))
        }

        Column(modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp)) {
            PrimaryAction(
                label = if (state.worthALook > 0) {
                    "Show me the ${state.worthALook}"
                } else {
                    "See what changed"
                },
                onClick = onSeeSavings,
            )

            Text(
                text = "Undo this import",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clickable(onClick = onUndo)
                    .padding(vertical = 12.dp),
            )
        }
    }
}

/**
 * The analysis that ran while the user was reading the count.
 *
 * This is the moment the app has to earn: a number of transactions imported is
 * bookkeeping, and "3 things worth a look" is the reason any of it was worth
 * doing. Only that last row is orange.
 */
@Composable
private fun WhileYouWereReading(state: ImportUiState.Done) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = "WHILE YOU WERE READING THAT",
            style = SectionLabelStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

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
            FindingRow(
                label = "Recurring payments found",
                value = state.recurringFound,
                emphasised = false,
            )
            FindingRow(
                label = "Merchants we couldn't name",
                value = state.unnamedMerchants,
                emphasised = false,
            )
            FindingRow(
                label = "Things worth a look",
                value = state.worthALook,
                emphasised = state.worthALook > 0,
            )
        }
    }
}

@Composable
private fun FindingRow(label: String, value: Int, emphasised: Boolean) {
    val colours = FinanceMate.colours

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (emphasised) {
                    colours.foundMoneyFill
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                },
            )
            .padding(horizontal = 20.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (emphasised) colours.foundMoneyText else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = value.toString(),
            style = AmountStyle.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize),
            color = if (emphasised) colours.foundMoney else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** The claim, restated at the one moment the user has just seen it be true. */
@Composable
private fun LocalWorkNote() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(8.dp)
                .background(FinanceMate.colours.moneyIn, CircleShape),
        )
        Text(
            text = "All of that was worked out on this phone. Nothing was sent anywhere.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
