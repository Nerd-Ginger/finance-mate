package dev.financemate.ui.import

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.financemate.ui.formatted
import java.time.format.DateTimeFormatter

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

/**
 * MIME types offered in the file picker.
 *
 * Deliberately broad: OFX and QIF have no registered MIME type, so many
 * providers report them as `application/octet-stream` or `text/plain`. Filtering
 * narrowly would grey out the user's own statement in the picker with no
 * explanation, which is a maddening failure to debug.
 */
private val STATEMENT_MIME_TYPES = arrayOf(
    "text/csv",
    "text/comma-separated-values",
    "text/plain",
    "application/x-ofx",
    "application/vnd.intu.qfx",
    "application/octet-stream",
    "*/*",
)

@Composable
public fun ImportScreen(
    state: ImportUiState,
    onFilePicked: (android.net.Uri) -> Unit,
    onConfirm: (ImportUiState.Preview) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(onFilePicked) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (state) {
            ImportUiState.Idle -> {
                PrivacyCard()
                Button(
                    onClick = { picker.launch(STATEMENT_MIME_TYPES) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Choose a statement file")
                }
                Text(
                    "Supports CSV, OFX, QFX, and QIF. Most banks offer at least " +
                        "one under \"Download transactions\". OFX and QFX are the " +
                        "most reliable — they carry the bank's own transaction ids.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            ImportUiState.Parsing -> Busy("Reading the file…")

            ImportUiState.Importing -> Busy("Saving…")

            is ImportUiState.Preview -> PreviewContent(
                state = state,
                onConfirm = { onConfirm(state) },
                onCancel = onReset,
            )

            is ImportUiState.Done -> DoneContent(state, onReset)

            is ImportUiState.Failed -> {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Text(state.message, Modifier.padding(16.dp))
                }
                OutlinedButton(onClick = onReset) { Text("Try another file") }
            }
        }
    }
}

@Composable
private fun PrivacyCard() {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Your statement stays on this device", style = MaterialTheme.typography.titleMedium)
            Text(
                "The file is read and analysed here. No part of it is uploaded, " +
                    "and this works with no network connection at all.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun PreviewContent(
    state: ImportUiState.Preview,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            state.fileName?.let {
                Text(it, style = MaterialTheme.typography.titleMedium)
            }
            Text(state.formatDescription, style = MaterialTheme.typography.bodyMedium)
            Text(
                "${state.rowCount} transactions found",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (state.errorCount > 0) {
                Text(
                    "${state.errorCount} rows could not be read and will be skipped.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }

    // Showing real rows before writing anything is the whole point of this
    // screen: an inverted sign or a swapped date column looks perfectly
    // plausible in aggregate but is obvious on a handful of actual transactions.
    if (state.parseResult.transactions.isNotEmpty()) {
        Text("Check these look right", style = MaterialTheme.typography.titleMedium)
        state.parseResult.transactions.take(5).forEach { transaction ->
            Card {
                Column(Modifier.padding(12.dp)) {
                    Text(transaction.rawDescription, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${transaction.postedDate.format(DATE_FORMAT)} · " +
                            transaction.amount.formatted(),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        if (state.rowCount > 5) {
            Text(
                "…and ${state.rowCount - 5} more",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            "Money you spent should show as negative. If the signs are the wrong " +
                "way round, cancel — importing would record your spending as income.",
            style = MaterialTheme.typography.bodySmall,
        )
    }

    Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth()) {
        Text("Import ${state.rowCount} transactions")
    }
    OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
        Text("Cancel")
    }
}

@Composable
private fun DoneContent(state: ImportUiState.Done, onReset: () -> Unit) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Import complete", style = MaterialTheme.typography.titleMedium)
            Text("${state.imported} transactions added.", style = MaterialTheme.typography.bodyMedium)

            if (state.duplicates > 0) {
                // Without this explanation "0 of 240 imported" reads as a failure,
                // when it is the correct result of re-importing an overlapping
                // date range.
                Text(
                    "${state.duplicates} were already in your ledger and were skipped.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (state.isLikelyReimport) {
                Text(
                    "This statement almost entirely overlapped what you already " +
                        "had, which is exactly what should happen when you " +
                        "re-download the same date range.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
    Button(onClick = onReset, modifier = Modifier.fillMaxWidth()) {
        Text("Import another file")
    }
}

@Composable
private fun Busy(message: String) {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CircularProgressIndicator()
        Text(message, style = MaterialTheme.typography.bodyLarge)
    }
}
