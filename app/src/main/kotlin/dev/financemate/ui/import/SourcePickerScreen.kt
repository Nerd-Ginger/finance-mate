package dev.financemate.ui.import

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.financemate.core.parsing.csv.BankProfiles
import dev.financemate.ui.theme.EgressStyle
import dev.financemate.ui.theme.FinanceMate

/**
 * Where the transactions are coming from.
 *
 * One option is orange, and it is the one that works. The other two are shown
 * because the app will have them and hiding them would make the file route look
 * like the only thing FinanceMate can ever do — but they are drawn as
 * unavailable and say so, because an option that looks live and does nothing is
 * worse than an honest gap.
 *
 * The unrecognised-CSV case is named here, before it can happen. Told in advance,
 * a user meets column mapping as an expected step; told afterwards, they meet it
 * as a failure.
 */
@Composable
public fun SourcePickerScreen(
    onChooseFile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = "One month is enough to start. The file is read here and then " +
                "encrypted into your ledger.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SourceCard(
                title = "Statement file",
                formats = "CSV · OFX · QFX · QIF",
                detail = "Download from your bank's website, then pick it here.",
                available = true,
                onClick = onChooseFile,
            )
            SourceCard(
                title = "Screenshot",
                formats = null,
                detail = "Read on-device. You'll confirm each row it thinks it saw.",
                available = false,
                onClick = {},
            )
            SourceCard(
                title = "Type it in",
                formats = null,
                detail = "For one account, or to try it before exporting anything.",
                available = false,
                onClick = {},
            )
        }

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
                    .background(FinanceMate.colours.foundMoney, CircleShape),
            )
            Text(
                // Counted from BankProfiles rather than written here, so adding a
                // profile cannot leave this sentence quietly wrong.
                text = "We recognize ${BankProfiles.AUTO_DETECTED.size} banks " +
                    "automatically. If yours isn't one, we'll show you the first " +
                    "rows and ask what they mean.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * One route in.
 *
 * Unavailable cards keep their description. Reducing them to a greyed title
 * would leave the user unable to tell what they are waiting for.
 */
@Composable
private fun SourceCard(
    title: String,
    formats: String?,
    detail: String,
    available: Boolean,
    onClick: () -> Unit,
) {
    val colours = FinanceMate.colours

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (available) {
                    colours.foundMoneyFill
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                },
                shape = RoundedCornerShape(14.dp),
            )
            .border(
                width = 1.dp,
                color = if (available) {
                    colours.foundMoneyBorder
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                shape = RoundedCornerShape(14.dp),
            )
            .clickable(enabled = available, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (available) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            if (available) {
                Text(
                    text = "→",
                    style = MaterialTheme.typography.titleLarge,
                    color = colours.foundMoneyLink,
                )
            } else {
                Text(
                    // The honest label. "Coming soon" would be a promise with no
                    // date behind it; this just says what is true today.
                    text = "NOT BUILT YET",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        formats?.let {
            Text(
                text = it,
                style = EgressStyle.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize),
                color = colours.foundMoneyText,
            )
        }

        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
