package com.speak.app.ui.nav

import android.content.Intent
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.speak.app.di.AppContainer
import com.speak.app.ui.conversation.ConversationScreen
import com.speak.app.ui.conversation.ConversationViewModel
import com.speak.app.ui.drill.DrillScreen
import com.speak.app.ui.drill.DrillViewModel
import com.speak.app.ui.progress.MistakesScreen
import com.speak.app.ui.progress.ProgressScreen
import com.speak.app.ui.progress.ProgressViewModel
import com.speak.app.ui.progress.WeeklyScreen
import com.speak.app.ui.readaloud.ReadAloudScreen
import com.speak.app.ui.readaloud.ReadAloudViewModel
import com.speak.app.ui.settings.SettingsScreen
import com.speak.app.ui.settings.SettingsViewModel
import com.speak.app.ui.theme.SpeakIcons

private enum class TopLevel(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val title: String
) {
    TALK("talk", "Talk", SpeakIcons.Mic, "Speak"),
    READ("read", "Read", SpeakIcons.MenuBook, "Read aloud"),
    DRILL("drill", "Drill", SpeakIcons.Repeat, "Your mistakes"),
    PROGRESS("progress", "Progress", SpeakIcons.Insights, "Progress")
}

private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_MISTAKES = "mistakes"
private const val ROUTE_WEEKLY = "weekly"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeakNavHost(container: AppContainer) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val context = LocalContext.current

    val topLevel = TopLevel.entries.firstOrNull { it.route == currentRoute }
    val title = topLevel?.title ?: when (currentRoute) {
        ROUTE_SETTINGS -> "Settings"
        ROUTE_MISTAKES -> "Mistakes you keep making"
        ROUTE_WEEKLY -> "This week"
        else -> "Speak"
    }
    val isTopLevel = topLevel != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    if (!isTopLevel) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(SpeakIcons.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (currentRoute != ROUTE_SETTINGS) {
                        IconButton(onClick = { navController.navigate(ROUTE_SETTINGS) }) {
                            Icon(SpeakIcons.Settings, contentDescription = "Settings")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            if (isTopLevel) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.background) {
                    TopLevel.entries.forEach { destination ->
                        val selected = backStackEntry?.destination?.hierarchy
                            ?.any { it.route == destination.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(destination.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = TopLevel.TALK.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(TopLevel.TALK.route) {
                val viewModel: ConversationViewModel =
                    viewModel(factory = ConversationViewModel.factory(container))
                ConversationScreen(
                    viewModel = viewModel,
                    onOpenTtsSettings = {
                        runCatching {
                            context.startActivity(
                                Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
                            )
                        }
                    },
                    onOpenModelDownload = { navController.navigate(ROUTE_SETTINGS) }
                )
            }

            composable(TopLevel.READ.route) {
                val viewModel: ReadAloudViewModel =
                    viewModel(factory = ReadAloudViewModel.factory(container))
                ReadAloudScreen(viewModel = viewModel)
            }

            composable(TopLevel.DRILL.route) {
                val viewModel: DrillViewModel =
                    viewModel(factory = DrillViewModel.factory(container))
                DrillScreen(viewModel = viewModel)
            }

            composable(TopLevel.PROGRESS.route) {
                val viewModel: ProgressViewModel =
                    viewModel(factory = ProgressViewModel.factory(container))
                ProgressScreen(
                    viewModel = viewModel,
                    onOpenMistakes = { navController.navigate(ROUTE_MISTAKES) },
                    onOpenWeekly = { navController.navigate(ROUTE_WEEKLY) }
                )
            }

            composable(ROUTE_MISTAKES) {
                val viewModel: ProgressViewModel =
                    viewModel(factory = ProgressViewModel.factory(container))
                MistakesScreen(viewModel = viewModel)
            }

            composable(ROUTE_WEEKLY) {
                val viewModel: ProgressViewModel =
                    viewModel(factory = ProgressViewModel.factory(container))
                WeeklyScreen(viewModel = viewModel)
            }

            composable(ROUTE_SETTINGS) {
                val viewModel: SettingsViewModel =
                    viewModel(factory = SettingsViewModel.factory(container))
                SettingsScreen(viewModel = viewModel)
            }
        }
    }
}
