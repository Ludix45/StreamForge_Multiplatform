package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.interop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.*
import platform.Foundation.NSURL
import platform.QuartzCore.CATransaction
import platform.QuartzCore.kCATransactionDisableActions
import platform.UIKit.UIView

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun VideoPlayer(
    url: String,
    title: String,
    modifier: Modifier,
    onBack: () -> Unit
) {
    val player = remember {
        val nsUrl = NSURL.URLWithString(url) ?: return@remember AVPlayer()
        AVPlayer.playerWithURL(nsUrl)
    }

    val playerLayer = remember { AVPlayerLayer.playerLayerWithPlayer(player) }
    val playerContainer = remember {
        UIView().apply {
            layer.addSublayer(playerLayer)
        }
    }

    DisposableEffect(Unit) {
        player.play()
        onDispose {
            player.pause()
        }
    }

    Box(modifier = modifier.background(Color.Black)) {
        UIKitView(
            factory = { playerContainer },
            modifier = modifier,
            update = { view ->
                CATransaction.begin()
                CATransaction.setValue(true, kCATransactionDisableActions)
                playerLayer.setFrame(view.bounds)
                CATransaction.commit()
            }
        )
    }
}
