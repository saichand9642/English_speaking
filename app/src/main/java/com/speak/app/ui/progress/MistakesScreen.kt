package com.speak.app.ui.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.speak.app.domain.model.MistakeCategory
import com.speak.app.ui.components.CategoryChip
import com.speak.app.ui.components.EmptyState
import com.speak.app.ui.components.SectionCard

/**
 * Every recurring mistake, ranked by how often it has happened.
 *
 * This list is the real product. Most speakers cycle through the same handful of
 * errors, and fixing those is where nearly all the available improvement is.
 */
@Composable
fun MistakesScreen(viewModel: ProgressViewModel, modifier: Modifier = Modifier) {
    val mistakes by viewModel.rankedMistakes.collectAsStateWithLifecycle()

    if (mistakes.isEmpty()) {
        Column(modifier.fillMaxSize()) {
            EmptyState(
                title = "No mistakes logged yet",
                body = "Once you have had a few conversations, the errors you repeat will be collected here, most frequent first."
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp, end = 20.dp, top = 8.dp, bottom = 40.dp
        )
    ) {
        item {
            Text(
                "Ranked by how many times you have made each one.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))
        }

        items(mistakes) { mistake ->
            SectionCard {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
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
                    }
                    Text(
                        "${mistake.occurrences}×",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (mistake.explanation.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        mistake.explanation,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(10.dp))
                CategoryChip(MistakeCategory.from(mistake.category).label)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
