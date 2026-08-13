package com.speak.app.ui.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.speak.app.data.db.RangeTotals
import com.speak.app.ui.components.EmptyState
import com.speak.app.ui.components.SectionCard
import com.speak.app.ui.components.StatTile
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The last seven days, next to the seven before them.
 *
 * Comparison is framed factually and without praise or blame. A week where you
 * spoke less is reported as a week where you spoke less; the app's job is to show
 * what happened, not to manage your feelings about it.
 */
@Composable
fun WeeklyScreen(viewModel: ProgressViewModel, modifier: Modifier = Modifier) {
    val thisWeek by viewModel.thisWeek.collectAsStateWithLifecycle()
    val lastWeek by viewModel.lastWeek.collectAsStateWithLifecycle()
    val focus by viewModel.weeklyFocus.collectAsStateWithLifecycle()

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        if (thisWeek.turns == 0 && lastWeek.turns == 0) {
            EmptyState(
                title = "No practice this week",
                body = "Once you have spoken a few times, this screen will summarise the week and compare it with the one before."
            )
            return@Column
        }

        SectionCard(title = "Last 7 days") {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatTile(
                    label = "days practised",
                    value = "${thisWeek.activeDays}",
                    caption = "of 7"
                )
                StatTile(
                    label = "minutes spoken",
                    value = "${(thisWeek.durationMs / 60_000L)}"
                )
                StatTile(label = "words", value = "${thisWeek.words}")
            }
            Spacer(Modifier.height(20.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatTile(
                    label = "average pace",
                    value = "${thisWeek.avgWordsPerMinute.roundToInt()}",
                    caption = "words per minute"
                )
                StatTile(
                    label = "mistakes / 100 words",
                    value = String.format("%.1f", thisWeek.mistakesPerHundredWords)
                )
                StatTile(label = "mistakes", value = "${thisWeek.mistakes}")
            }
        }
        Spacer(Modifier.height(14.dp))

        SectionCard(title = "Compared with the week before") {
            ComparisonRow(
                label = "Days practised",
                current = thisWeek.activeDays.toDouble(),
                previous = lastWeek.activeDays.toDouble(),
                lowerIsBetter = false,
                format = { it.roundToInt().toString() }
            )
            ComparisonRow(
                label = "Minutes spoken",
                current = (thisWeek.durationMs / 60_000L).toDouble(),
                previous = (lastWeek.durationMs / 60_000L).toDouble(),
                lowerIsBetter = false,
                format = { it.roundToInt().toString() }
            )
            ComparisonRow(
                label = "Speaking pace",
                current = thisWeek.avgWordsPerMinute,
                previous = lastWeek.avgWordsPerMinute,
                lowerIsBetter = false,
                format = { "${it.roundToInt()} wpm" }
            )
            ComparisonRow(
                label = "Mistakes per 100 words",
                current = thisWeek.mistakesPerHundredWords,
                previous = lastWeek.mistakesPerHundredWords,
                lowerIsBetter = true,
                format = { String.format("%.1f", it) }
            )
        }
        Spacer(Modifier.height(14.dp))

        if (focus.isNotEmpty()) {
            SectionCard(title = "Worth working on next week") {
                Text(
                    "These came up more than once. Try to use them correctly on purpose while you talk.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(14.dp))
                focus.forEach { mistake ->
                    Row {
                        Text(
                            mistake.wrong,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                            textDecoration = TextDecoration.LineThrough
                        )
                        Text(
                            "  →  ",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            mistake.fixed,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun ComparisonRow(
    label: String,
    current: Double,
    previous: Double,
    lowerIsBetter: Boolean,
    format: (Double) -> String
) {
    val delta = current - previous
    val meaningful = abs(delta) > 0.05 && previous > 0.0
    val improved = if (lowerIsBetter) delta < 0 else delta > 0

    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Row {
            Text(format(current), style = MaterialTheme.typography.bodyMedium)
            if (meaningful) {
                Text(
                    text = "  ${if (delta > 0) "+" else "−"}${format(abs(delta))}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (improved) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            } else if (previous == 0.0) {
                Text(
                    "  first week",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
