package com.poddersinthechat.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
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
                    feedRepository = app.feedRepository,
                    playerManager = app.playerManager
                ) as T
            }
        }

        setContent {
            PoddersTheme {
                val viewModel: PodcastViewModel = viewModel(factory = factory)
                MainScreen(viewModel = viewModel)
            }
        }
    }
}
