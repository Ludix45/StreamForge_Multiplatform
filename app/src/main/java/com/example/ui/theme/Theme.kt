package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Schema colori principale per il tema scuro
private val DarkColorScheme = darkColorScheme(
    primary = ForgeOrange,
    onPrimary = Color.White,
    secondary = ForgeGold,
    onSecondary = Color.Black,
    tertiary = CrimsonOffset,
    background = DarkBackground,
    onBackground = SoftWhite,
    surface = DarkSurface,
    onSurface = SoftWhite,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = SoftWhite
)

/**
 * MyApplicationTheme
 * 
 * Il tema base dell'applicazione che applica lo schema di colori scuro, 
 * i font tipografici personalizzati e un look "cinematografico" per un app di streaming.
 */
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false, // Ignoriamo i colori dinamici di sistema per mantenere la UI premium
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
