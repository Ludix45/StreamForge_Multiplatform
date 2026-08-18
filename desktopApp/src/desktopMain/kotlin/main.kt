package com.aistudio.streamforge.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

/** Starts the desktop shell; Android keeps its own MainActivity unchanged. */
fun main() = application {
    Window(title = "StreamForge Desktop",
        icon = painterResource("streamforge-logo.png"),
        state = rememberWindowState(placement = WindowPlacement.Maximized),
        onCloseRequest = ::exitApplication) {
        // A single explicit dark palette prevents Material components from inheriting
        // black text from the light default theme over the application's dark surfaces.
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = Color(0xFFFF8A00),
                onPrimary = Color(0xFF17120A),
                background = Color(0xFF101218),
                onBackground = Color(0xFFF3F5F8),
                surface = Color(0xFF1A1F29),
                onSurface = Color(0xFFF3F5F8),
                surfaceVariant = Color(0xFF2A303B),
                onSurfaceVariant = Color(0xFFD8DEE9),
            ),
        ) { StreamForgeDesktopApp() }
    }
}
