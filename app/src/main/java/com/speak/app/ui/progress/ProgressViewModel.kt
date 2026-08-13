package com.speak.app.ui.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.speak.app.data.db.DailyStats
import com.speak.app.data.db.MistakeFrequency
import com.speak.app.data.db.RangeTotals
import com.speak.app.data.db.WordDifficulty
import com.speak.app.di.AppContainer
import com.speak.app.domain.model.MistakeCategory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope

data class ProgressUiState(
    val streak: Int = 0,
    val daily: List<DailyStats> = emptyList(),
    val categories: List<Pair<String, Int>> = emptyList(),
    val dueDrills: Int = 0,
    val totalDrills: Int = 0
) {
    val wpmPoints: List<ChartPoint>
        get() = daily.filter { it.avgWordsPerMinute > 0 }
            .map { ChartPoint(it.epochDay.toFloat(), it.avgWordsPerMinute.toFloat()) }

    val mistakeRatePoints: List<ChartPoint>
        get() = daily.filter { it.words > 0 }
            .map { ChartPoint(it.epochDay.toFloat(), it.mistakesPerHundredWords.toFloat()) }

    val fillerPoints: List<ChartPoint>
        get() = daily.filter { it.words > 0 }
            .map { ChartPoint(it.epochDay.toFloat(), it.fillersPerHundredWords.toFloat()) }

    val latestWpm: Int get() = daily.lastOrNull { it.avgWordsPerMinute > 0 }?.avgWordsPerMinute?.toInt() ?: 0
    val latestMistakeRate: Double get() = daily.lastOrNull { it.words > 0 }?.mistakesPerHundredWords ?: 0.0
    val latestFillerRate: Double get() = daily.lastOrNull { it.words > 0 }?.fillersPerHundredWords ?: 0.0
    val totalMinutes: Int get() = (daily.sumOf { it.durationMs } / 60_000L).toInt()
    val hasData: Boolean get() = daily.isNotEmpty()
}

class ProgressViewModel(container: AppContainer) : ViewModel() {

    private val repository = container.repository

    val state: StateFlow<ProgressUiState> = combine(
        repository.streak(),
        repository.dailyStats(days = 30),
        repository.categoryBreakdown(days = 30),
        repository.dueDrillCount(),
        repository.totalDrillCount()
    ) { streak, daily, categories, due, total ->
        ProgressUiState(
            streak = streak,
            daily = daily,
            categories = categories.map { MistakeCategory.from(it.category).label to it.occurrences },
            dueDrills = due,
            totalDrills = total
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressUiState())

    val rankedMistakes: StateFlow<List<MistakeFrequency>> =
        repository.rankedMistakes(minOccurrences = 1, limit = 60)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val troublesomeWords: StateFlow<List<WordDifficulty>> =
        repository.troublesomeWords(limit = 12)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val thisWeek: StateFlow<RangeTotals> = repository.weekTotals(weeksAgo = 0)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RangeTotals.EMPTY)

    val lastWeek: StateFlow<RangeTotals> = repository.weekTotals(weeksAgo = 1)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RangeTotals.EMPTY)

    /** The three errors worth working on next, for the weekly summary. */
    val weeklyFocus: StateFlow<List<MistakeFrequency>> =
        repository.rankedMistakes(minOccurrences = 2, limit = 3)
            .map { it }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { ProgressViewModel(container) }
        }
    }
}
