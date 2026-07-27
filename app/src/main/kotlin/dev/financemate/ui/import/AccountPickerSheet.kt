package dev.financemate.ui.import

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.financemate.core.model.Account
import dev.financemate.ui.theme.FinanceMate
import dev.financemate.ui.theme.MonoMetaStyle

/**
 * Which account a statement is being read into.
 *
 * Until now the first import silently created one holding account and every
 * later import went into it, which is fine for one card and wrong for anybody
 * with a current account and a credit card: their histories merge, and the
 * recurring-payment detector then reasons about a timeline that never existed.
 *
 * Choosing is offered at the checkpoint rather than in a settings screen because
 * that is the moment the question has an obvious answer — the user is looking at
 * the file they just exported.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun AccountPickerSheet(
    accounts: List<Account>,
    selected: Account?,
    onSelect: (Account) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Read this file as",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )

            accounts.forEach { account ->
                AccountRow(
                    account = account,
                    isSelected = account.id == selected?.id,
                    onClick = { onSelect(account) },
                )
            }

            if (accounts.isEmpty()) {
                Text(
                    text = "No accounts yet. This import will create one.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AccountRow(
    account: Account,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val colours = FinanceMate.colours

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = account.displayName,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = buildString {
                    append(account.institution)
                    // The last four digits are how a user recognises which card
                    // this is, and they are the only part of an account number
                    // the app ever holds.
                    account.mask?.let { append(" ····$it") }
                },
                style = MonoMetaStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (isSelected) {
            Text(
                text = "✓",
                style = MaterialTheme.typography.titleMedium,
                color = colours.foundMoney,
            )
        }
    }
}
