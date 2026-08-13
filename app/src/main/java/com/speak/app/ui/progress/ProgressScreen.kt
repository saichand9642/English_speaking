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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.speak.app.ui.components.EmptyState
import com.speak.app.ui.components.SecondaryAction
import com.speak.app.ui.components.SectionCard
import com.speak.app.ui.components.StatTile

@Composable
fun ProgressScreen(
    viewModel: ProgressViewModel,
    onOpenMistakes: () -> Unit,
    onOpenWeekly: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val words by viewModel.troublesomeWords.collectAsStateWithLifecycle()

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        if (!state.hasData) {
            EmptyState(
                title = "Nothing to show yet",
                body = "Have a conversation and your speaking speed, hesitations and mistakes will start appearing here."
            )
            return@Column
        }

        SectionCard {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatTile(
                    label = if (state.streak == 1) "day in a row" else "days in a row",
                    value = "${state.streak}"
                )
                StatTile(label = "minutes spoken", value = "${state.totalMinutes}")
                StatTile(label = "drills due", value = "${state.dueDrills}")
            }
        }
        Spacer(Modifier.height(14.dp))

        SectionCard(title = "Speaking speed") {
            TrendSection(
                title = "Words per minute",
                currentValue = "${state.latestWpm}",
                points = state.wpmPoints,
                caption = "The shaded band is the natural conversational range, 120 to 150.",
                referenceBand = 120f..150f
            )
        }
        Spacer(Modifier.height(14.dp))

        SectionCard(title = "Accuracy") {
            TrendSection(
                title = "Mistakes per 100 words",
                currentValue = String.format("%.1f", state.latestMistakeRate),
                points = state.mistakeRatePoints,
                caption = "Lower is better. A rate, not a count, so longer sessions are comparable."
            )
        }
        Spacer(Modifier.height(14.dp))

        SectionCard(title = "Hesitation") {
            TrendSection(
                title = "Filler words per 100 words",
                currentValue = String.format("%.1f", state.latestFillerRate),
                points = state.fillerPoints,
                caption = "Counts um, uh, er and similar. Not \"like\" or \"so\", which have real uses."
            )
        }
        Spacer(Modifier.height(14.dp))

        if (state.categories.isNotEmpty()) {
            SectionCard(title = "Where the mistakes are") {
                CategoryBars(state.categories)
            }
            Spacer(Modifier.height(14.dp))
        }

        if (words.isNotEmpty()) {
            SectionCard(title = "Words that came out unclearly") {
                words.forEach { word ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 5.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(word.word, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "${word.occurrences}×",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        SecondaryAction(text = "Mistakes you keep making", onClick = onOpenMistakes)
        Spacer(Modifier.height(10.dp))
        SecondaryAction(text = "This week", onClick = onOpenWeekly)
        Spacer(Modifier.height(40.dp))
    }
}
