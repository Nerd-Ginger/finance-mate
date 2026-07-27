package dev.financemate.ui.import

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.financemate.core.model.Account
import dev.financemate.core.model.ImportBatchId
import dev.financemate.ui.onboarding.PrimaryAction

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

/**
 * The import flow: pick a source, check what it will do, commit, see the result.
 *
 * The steps are driven by [ImportUiState] rather than by separate navigation
 * routes. They are strictly linear and each one replaces the last, so a back
 * stack would only be a second, weaker copy of the state machine — and the two
 * could then disagree, which is how a user ends up looking at a checkpoint for a
 * file that has already been imported.
 *
 * Back is handled explicitly instead: from the checkpoint it returns to the
 * source picker so a different file can be chosen, and everywhere else it leaves
 * the flow.
 */
@Composable
public fun ImportScreen(
    state: ImportUiState,
    onFilePicked: (android.net.Uri) -> Unit,
    onConfirm: (ImportUiState.Preview) -> Unit,
    onChangeAccount: (Account) -> Unit,
    onUndo: (ImportBatchId) -> Unit,
    onReset: () -> Unit,
    /** Leaves the import flow entirely, returning to the savings view. */
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(onFilePicked) }

    // Backing out of a checkpoint means "wrong file", not "leave import".
    BackHandler(enabled = state is ImportUiState.Preview || state is ImportUiState.Failed) {
        onReset()
    }

    when (state) {
        ImportUiState.Idle -> SourcePickerScreen(
            onChooseFile = { picker.launch(STATEMENT_MIME_TYPES) },
            modifier = modifier,
        )

        ImportUiState.Parsing -> Busy("Reading the file…", modifier)

        ImportUiState.Importing -> Busy("Saving…", modifier)

        is ImportUiState.Preview -> when (val checkpoint = state.checkpoint) {
            // Only ever seen for the moment after an account change, while the
            // counts are recomputed against the new account.
            null -> Busy("Checking your ledger…", modifier)

            else -> CheckpointScreen(
                fileName = state.fileName,
                formatDescription = state.formatDescription,
                account = state.account,
                checkpoint = checkpoint,
                accounts = state.accounts,
                onChangeAccount = onChangeAccount,
                onImport = { onConfirm(state) },
                onCancel = onReset,
                modifier = modifier,
            )
        }

        is ImportUiState.Done -> ResultScreen(
            state = state,
            onSeeSavings = onDone,
            onUndo = { onUndo(state.batchId) },
            modifier = modifier,
        )

        is ImportUiState.Failed -> FailedContent(
            message = state.message,
            onRetry = onReset,
            modifier = modifier,
        )
    }
}

@Composable
private fun FailedContent(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 28.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        PrimaryAction(label = "Try another file", onClick = onRetry)
    }
}

@Composable
private fun Busy(message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        // Neutral, not orange. Orange is reserved for money the app found and for
        // the one primary action; spending it on "please wait" cheapens it
        // everywhere it matters.
        CircularProgressIndicator(color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
