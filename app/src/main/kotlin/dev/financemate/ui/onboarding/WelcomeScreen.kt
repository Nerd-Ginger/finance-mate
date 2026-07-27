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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.financemate.ui.theme.FinanceMate

/**
 * The first screen, and the only one before the first file.
 *
 * One screen rather than a carousel: there is nothing here worth three swipes,
 * and every extra tap between install and first import costs users who were
 * already unsure. The privacy claim is three checkable facts rather than a
 * paragraph of reassurance, and it links to the proof — a sceptic can verify it
 * before handing over a statement, which is the correct order.
 */
@Composable
public fun WelcomeScreen(
    onImport: () -> Unit,
    onSeeEgress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 28.dp),
    ) {
        Spacer(Modifier.height(56.dp))

        Text(
            text = "FinanceMate",
            style = MaterialTheme.typography.labelSmall,
            color = FinanceMate.colours.foundMoney,
        )

        Text(
            text = "Find the money you forgot you were spending.",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 28.dp),
        )

        Text(
            text = "Import a statement you already have. FinanceMate reads it on " +
                "this phone and tells you what's leaking — duplicate services, " +
                "quiet price rises, fees.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 20.dp),
        )

        PrivacyFacts(modifier = Modifier.padding(top = 40.dp))

        Spacer(Modifier.weight(1f))

        PrimaryAction(
            label = "Import a statement",
            onClick = onImport,
        )

        Text(
            text = "See what leaves this device",
            style = MaterialTheme.typography.bodyMedium,
            color = FinanceMate.colours.foundMoneyLink,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .clickable(onClick = onSeeEgress)
                .padding(vertical = 14.dp),
        )

        Spacer(Modifier.height(24.dp))
    }
}

/**
 * Three facts, not a privacy policy.
 *
 * Each one is falsifiable by the user: they can look for a login prompt, look
 * for a sign-up, and turn off the network. A claim someone can check themselves
 * is worth more than a paragraph they have to take on faith.
 */
@Composable
private fun PrivacyFacts(modifier: Modifier = Modifier) {
    val facts = listOf(
        "No bank login, ever",
        "No account, no server",
        // "Airplane mode" is what the Android setting is actually called on a US
        // device. This line asks the user to go and check, so it has to match
        // the label they will be looking for.
        "Works with airplane mode on",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(12.dp),
            ),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        facts.forEach { fact ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(FinanceMate.colours.foundMoney, CircleShape),
                )
                Text(
                    text = fact,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/**
 * The one filled-orange element on the screen.
 *
 * A Material `Button` would work, but it brings its own elevation, ripple and
 * min-height, and the design's control is a plain 56dp pill. Fewer overrides
 * than corrections.
 */
@Composable
internal fun PrimaryAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(
                color = if (enabled) {
                    FinanceMate.colours.foundMoney
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                shape = RoundedCornerShape(999.dp),
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}
