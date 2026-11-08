package org.betterlyrics.composer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.betterlyrics.composer.ui.ComposerApp
import org.betterlyrics.composer.ui.theme.ComposerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ComposerTheme {
                ComposerApp()
            }
        }
    }
}
