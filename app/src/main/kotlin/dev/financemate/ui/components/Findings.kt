package dev.financemate.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.financemate.ui.theme.FinanceMate
import dev.financemate.ui.theme.SectionLabelStyle

/**
 * How sure the engine is about a finding.
 *
 * Confidence is expressed by **how the card is drawn**, not by a badge alone: a
 * certain finding is filled orange, a likely one keeps a hairline but loses the
 * fill, and a guess drops out of orange entirely into a dashed neutral outline.
 * The reader takes in certainty before they read a single word, which is the
 * point — a maybe should never look like a fact.
 */
enum class FindingConfidence {
    /** Solid orange. The engine is sure. */
    CERTAIN,

    /** Orange hairline, neutral fill. Probably right, worth a look. */
    LIKELY,

    /** Dashed neutral. A guess, offered without pretending otherwise. */
    UNCERTAIN,
    ;

    companion object {
        fun from(score: Double): FindingConfidence = when {
            score >= 0.85 -> CERTAIN
            score >= 0.70 -> LIKELY
            else -> UNCERTAIN
        }
    }
}

/**
 * The container every finding is drawn in.
 *
 * One component, so the confidence encoding cannot drift between screens — a
 * duplicate on the savings hub and the same duplicate on a detail page must not
 * disagree about how sure the app is.
 */
@Composable
fun FindingCard(
    confidence: FindingConfidence,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colours = FinanceMate.colours
    val shape = RoundedCornerShape(12.dp)

    val styled = when (confidence) {
        FindingConfidence.CERTAIN -> modifier
            .fillMaxWidth()
            .background(colours.foundMoneyFill, shape)
            .border(BorderStroke(1.dp, colours.foundMoneyBorder), shape)

        FindingConfidence.LIKELY -> modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, shape)
            .border(BorderStroke(1.dp, colours.foundMoneyBorder.copy(alpha = 0.55f)), shape)

        FindingConfidence.UNCERTAIN -> modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, shape)
            .dashedBorder(colours.uncertain)
    }

    Column(
        modifier = styled.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/**
 * A confidence chip.
 *
 * Only [FindingConfidence.CERTAIN] gets a filled orange chip. The rest are
 * outlined and neutral, because a bright badge reading "61%" still registers as
 * emphasis at a glance, and the entire point is that it should not.
 */
@Composable
fun ConfidenceChip(
    confidence: FindingConfidence,
    score: Double,
    modifier: Modifier = Modifier,
) {
    val colours = FinanceMate.colours
    val shape = RoundedCornerShape(3.dp)

    if (confidence == FindingConfidence.CERTAIN) {
        Text(
            text = "CERTAIN",
            style = SectionLabelStyle,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = modifier
                .background(colours.foundMoney, shape)
                .padding(horizontal = 8.dp, vertical = 5.dp),
        )
    } else {
        Text(
            text = "${(score * 100).toInt()}% MATCH",
            style = SectionLabelStyle,
            color = colours.uncertainText,
            modifier = modifier
                .border(BorderStroke(1.dp, colours.uncertain), shape)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/** Mono, uppercase, wide-tracked section heading. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = SectionLabelStyle,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** A label/value row, with the value in tabular figures so columns line up. */
@Composable
fun DetailRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    labelColour: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    valueColour: Color = MaterialTheme.colorScheme.onSurface,
    valueWeight: FontWeight = FontWeight.Normal,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = labelColour,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
            color = valueColour,
            fontWeight = valueWeight,
        )
    }
}

/**
 * A dashed outline.
 *
 * Compose has no dashed border. This is load-bearing rather than decorative —
 * the dashes are what tell the reader a finding is a guess — so it is worth the
 * custom draw.
 */
internal fun Modifier.dashedBorder(colour: Color): Modifier = drawBehind {
    val radius = CornerRadius(12.dp.toPx())
    val outline = RoundRect(
        left = 0f,
        top = 0f,
        right = size.width,
        bottom = size.height,
        cornerRadius = radius,
    )
    drawPath(
        path = Path().apply { addRoundRect(outline) },
        color = colour,
        style = Stroke(
            width = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f),
        ),
    )
}
