package com.example.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun VideoPlayer(
    url: String,
    title: String,
    modifier: Modifier = Modifier,
    onBack: () -> Unit
)
