package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.service.configureAppCheck
import com.example.ui.StudioScreen
import com.example.ui.StudioViewModel
import com.example.ui.theme.CinePromptTheme

class MainActivity : ComponentActivity() {

    private val viewModel: StudioViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureAppCheck(this, intent)
        enableEdgeToEdge()
        setContent {
            val isDarkTheme by viewModel.isDarkTheme.collectAsState()
            CinePromptTheme(darkTheme = isDarkTheme) {
                StudioScreen(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
