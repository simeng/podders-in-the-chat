package com.poddersinthechat.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.poddersinthechat.app.data.ThemeMode
import com.poddersinthechat.app.ui.screens.MainScreen
import com.poddersinthechat.app.ui.theme.PoddersTheme
import com.poddersinthechat.app.ui.viewmodel.PodcastViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as PoddersApp

        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return PodcastViewModel(
                    appContext = application,
                    feedRepository = app.feedRepository,
                    playerManager = app.playerManager,
                    recommendationsRepository = app.recommendationsRepository,
                    downloadRepository = app.downloadRepository,
                    settingsStore = app.settingsStore
                ) as T
            }
        }

        setContent {
            val viewModel: PodcastViewModel = viewModel(factory = factory)
            val themeMode by viewModel.themeMode.collectAsState()
            val darkTheme = when (themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.AUTO -> isSystemInDarkTheme()
            }
            PoddersTheme(darkTheme = darkTheme) {
                MainScreen(viewModel = viewModel)
            }
        }
    }
}
