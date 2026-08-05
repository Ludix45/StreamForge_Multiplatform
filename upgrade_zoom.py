import re

with open('app/src/main/java/com/example/ui/screens/Screens.kt', 'r') as f:
    content = f.read()

# 1. Add state variable
state_pattern = r"var showSubtitlesMenu by remember \{ mutableStateOf\(false\) \}"
state_replace = """var showSubtitlesMenu by remember { mutableStateOf(false) }
    // Stato per la funzione zoom/riempi (limita bordi neri)
    var isZoomed by remember { mutableStateOf(false) }"""
content = re.sub(state_pattern, state_replace, content)

# 2. Update AndroidView
androidview_pattern = r"update = \{ playerView ->\s*if \(playerView\.player != exoPlayer\) \{\s*playerView\.player = exoPlayer\s*\}\s*\}"
androidview_replace = """update = { playerView ->
                if (playerView.player != exoPlayer) {
                    playerView.player = exoPlayer
                }
                // Applica lo zoom (taglia i bordi neri) oppure adatta normalmente
                playerView.resizeMode = if (isZoomed) androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM else androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
            }"""
content = re.sub(androidview_pattern, androidview_replace, content)

# 3. Add toggle button
buttons_pattern = r"// Pulsante Sottotitoli\s*Box \{"
buttons_replace = """// Pulsante Riempi Schermo (Zoom) per tagliare i bordi neri
                IconButton(
                    onClick = { isZoomed = !isZoomed },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = if (isZoomed) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = "Espandi a tutto schermo",
                        tint = Color.White
                    )
                }

                // Pulsante Sottotitoli
                Box {"""
content = re.sub(buttons_pattern, buttons_replace, content)

with open('app/src/main/java/com/example/ui/screens/Screens.kt', 'w') as f:
    f.write(content)
