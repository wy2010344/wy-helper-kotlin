package org.wy.helper

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

@Composable
fun App() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize().safeContentPadding()) {
        }
    }
}
