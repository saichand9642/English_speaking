package com.speak.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.speak.app.domain.model.Correction
import com.speak.app.domain.model.DiffSpan
import com.speak.app.domain.model.PronunciationEvidence
import com.speak.app.domain.model.PronunciationNote
import com.speak.app.ui.theme.SpeakIcons

/**
 * The learner's sentence with the wrong parts struck through and the fixes
 * highlighted inline.
 *
 * Showing both at once, in place, is the point: seeing "I ~~go~~ went" in the
 * original sentence connects the fix to the thing that was actually said far more
 * directly than a separate corrected sentence underneath would.
 */
@Composable
fun DiffSentence(spans: List<DiffSpan>, modifier: Modifier = Modifier) {
    val removedColour = MaterialTheme.colorScheme.error
    val addedColour = MaterialTheme.colorScheme.primary
    val normalColour = MaterialTheme.colorScheme.onSurface

    val text: AnnotatedString = buildAnnotatedString {
        spans.forEachIndexed { index, span ->
            if (index > 0) append(" ")
            when (span.kind) {
                DiffSpan.Kind.UNCHANGED ->
                    withStyle(SpanStyle(color = normalColour)) { append(span.text) }

                DiffSpan.Kind.REMOVED ->
                    withStyle(
                        SpanStyle(
                            color = removedColour,
                            textDecoration = TextDecoration.LineThrough
                        )
                    ) { append(span.text) }

                DiffSpan.Kind.ADDED ->
                    withStyle(
                        SpanStyle(color = addedColour, fontWeight = FontWeight.SemiBold)
                    ) { append(span.text) }
            }
        }
    }

    Text(text = text, style = MaterialTheme.typography.bodyLarge, modifier = modifier)
}

/** One mistake: the change, the reason, and its category. */
@Composable
fun CorrectionRow(correction: Correction, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = correction.wrong,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
                textDecoration = TextDecoration.LineThrough
            )
            Text(
                text = "  →  ",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = correction.right,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = correction.explanation,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        CategoryChip(correction.category.label)
    }
}

@Composable
fun CategoryChip(label: String, modifier: Modifier = Modifier) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}

/**
 * A pronunciation note.
 *
 * The evidence line is not decoration. Read-aloud notes come from comparing
 * against a known sentence and are dependable; free-conversation notes come from
 * the recogniser's uncertainty and are only a hint. Saying which is which is the
 * honest thing to do, and stops the learner chasing a problem that may not exist.
 */
@Composable
fun PronunciationRow(
    note: PronunciationNote,
    onHearSlowly: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = note.word,
                    style = MaterialTheme.typography.titleMedium
                )
                if (note.heardAs != null && !note.heardAs.equals(note.word, ignoreCase = true)) {
                    Text(
                        text = "sounded like \"${note.heardAs}\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
            OutlinedButton(
                onClick = { onHearSlowly(note.word) },
                modifier = Modifier.heightIn(min = 48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(SpeakIcons.VolumeUp, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Slowly")
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = note.tip,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = when (note.evidence) {
                PronunciationEvidence.TARGET_COMPARISON ->
                    "Compared against the sentence on screen."
                PronunciationEvidence.LOW_CONFIDENCE ->
                    "Based on how clearly this word came through — it may have been fine."
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
