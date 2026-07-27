package dev.financemate.ui.import

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.financemate.core.data.import.ImportCheckpoint
import dev.financemate.core.model.Account
import dev.financemate.core.model.ParsedTransaction
import dev.financemate.core.money.Money
import dev.financemate.ui.components.dashedBorder
import dev.financemate.ui.formatted
import dev.financemate.ui.onboarding.PrimaryAction
import dev.financemate.ui.theme.AmountStyle
import dev.financemate.ui.theme.FinanceMate
import dev.financemate.ui.theme.MonoMetaStyle
import dev.financemate.ui.theme.SectionLabelStyle
import java.time.format.DateTimeFormatter

/**
 * The last screen before anything is written.
 *
 * A real checkpoint rather than a formality. The failure it exists to catch is
 * an inverted sign: a wrong bank profile turns spending into income, every total
 * stays plausible, and nobody notices for months. Three sample rows with at
 * least one credit among them make that visible to a human in about a second,
 * which no amount of parser confidence can substitute for.
 *
 * The counts are equally load-bearing. "240 to add, 0 already in your ledger"
 * turns a re-import from a worrying event into an obvious no-op.
 */
@Composable
public fun CheckpointScreen(
    fileName: String?,
    formatDescription: String,
    account: Account?,
    checkpoint: ImportCheckpoint,
    accounts: List<Account>,
    onChangeAccount: (Account) -> Unit,
    onImport: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSkipped by remember { mutableStateOf(false) }
    var choosingAccount by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp)
                .padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            ReadAsCard(
                fileName = fileName,
                formatDescription = formatDescription,
                account = account,
                onChange = { choosingAccount = true },
            )

            SampleRows(sample = checkpoint.sample)

            CountRow(checkpoint = checkpoint)

            if (checkpoint.skipped.isNotEmpty()) {
                SkippedRows(
                    checkpoint = checkpoint,
                    expanded = showSkipped,
                    onToggle = { showSkipped = !showSkipped },
                )
            }
        }

        Column(
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 20.dp),
        ) {
            PrimaryAction(
                label = when {
                    checkpoint.addsNothing -> "Nothing new to import"
                    checkpoint.rowsToAdd == 1 -> "Import 1 transaction"
                    else -> "Import ${checkpoint.rowsToAdd} transactions"
                },
                onClick = onImport,
                enabled = !checkpoint.addsNothing,
            )
            Text(
                text = "Cancel",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .clickable(onClick = onCancel)
                    .padding(vertical = 12.dp),
            )
        }
    }

    if (choosingAccount) {
        AccountPickerSheet(
            accounts = accounts,
            selected = account,
            onSelect = {
                onChangeAccount(it)
                choosingAccount = false
            },
            onDismiss = { choosingAccount = false },
        )
    }
}

/**
 * Which account this file is being read as.
 *
 * Prominent and changeable, because getting it wrong is both easy and
 * expensive: transactions land in the wrong account, and the recurring-payment
 * detector then reasons about a merged history that never existed.
 */
@Composable
private fun ReadAsCard(
    fileName: String?,
    formatDescription: String,
    account: Account?,
    onChange: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(12.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = fileName ?: formatDescription,
                style = MonoMetaStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Read as: ${account?.displayName ?: "a new account"}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = "Change",
            style = MaterialTheme.typography.labelLarge,
            color = FinanceMate.colours.foundMoneyLink,
            modifier = Modifier.clickable(onClick = onChange),
        )
    }
}

/** Three rows, chosen so a flipped sign has somewhere to show itself. */
@Composable
private fun SampleRows(sample: List<ParsedTransaction>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "DOES THIS LOOK RIGHT?",
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
            sample.forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            text = row.rawDescription,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = row.postedDate.format(DAY_MONTH),
                            style = MonoMetaStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Amount(row.amount)
                }
            }
        }

        Text(
            text = "Spending should be negative and pay positive. If those are the " +
                "wrong way round, the file was read with the wrong profile — " +
                "change it above.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * A signed amount.
 *
 * Income is the only coloured one. Colouring spending too would make every
 * purchase look like a warning, which is exhausting and untrue — the minus sign
 * already carries the meaning.
 */
@Composable
private fun Amount(amount: Money) {
    val colours = FinanceMate.colours
    val positive = amount.minorUnits > 0

    Text(
        text = (if (positive) "+" else "") + amount.formatted(),
        style = AmountStyle.copy(fontSize = MaterialTheme.typography.titleSmall.fontSize),
        color = if (positive) colours.moneyIn else colours.moneyOut,
    )
}

@Composable
private fun CountRow(checkpoint: ImportCheckpoint) {
    // IntrinsicSize.Min so all three tiles match the tallest. Without it the
    // labels wrap to different line counts and the row comes out ragged, which
    // reads as three unrelated boxes rather than one set of figures.
    Row(
        modifier = Modifier.height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CountTile(
            value = checkpoint.rowsToAdd.toString(),
            label = "rows to add",
            modifier = Modifier.weight(1f),
        )
        CountTile(
            value = checkpoint.alreadyInLedger.toString(),
            label = "already in ledger",
            modifier = Modifier.weight(1f),
        )
        CountTile(
            value = dateRangeOf(checkpoint),
            label = "date range",
            modifier = Modifier.weight(1f),
            small = true,
        )
    }
}

private fun dateRangeOf(checkpoint: ImportCheckpoint): String {
    val from = checkpoint.earliest ?: return "—"
    val to = checkpoint.latest ?: return "—"
    return if (from == to) from.format(DAY_MONTH) else "${from.format(DAY_MONTH)}\n${to.format(DAY_MONTH)}"
}

@Composable
private fun CountTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    small: Boolean = false,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(12.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = value,
            style = if (small) {
                MaterialTheme.typography.titleSmall
            } else {
                MaterialTheme.typography.headlineSmall
            },
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Rows the parser could not read.
 *
 * Dashed rather than solid, matching how the design system draws anything the
 * app is unsure about. Collapsed by default but never hidden: a user who
 * exported 243 rows and imported 240 is owed an account of the other three.
 */
@Composable
private fun SkippedRows(
    checkpoint: ImportCheckpoint,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val skipped = checkpoint.skipped

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .dashedBorder(FinanceMate.colours.uncertain)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .size(8.dp)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant, CircleShape),
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = if (skipped.size == 1) {
                        "1 row could not be read and will be skipped."
                    } else {
                        "${skipped.size} rows could not be read and will be skipped."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (expanded) "Hide them" else "Show them",
                    style = MaterialTheme.typography.bodySmall,
                    color = FinanceMate.colours.foundMoneyLink,
                    modifier = Modifier.clickable(onClick = onToggle),
                )
            }
        }

        if (expanded) {
            skipped.forEach { problem ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "${problem.location} — ${problem.message}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    // The offending text itself. It is raw statement content, so
                    // it is shown to the user and never travels further.
                    problem.rawContent?.let { raw ->
                        Text(
                            text = raw,
                            style = MonoMetaStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private val DAY_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM")
