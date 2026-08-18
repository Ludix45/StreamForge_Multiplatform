package com.aistudio.streamforge.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.foundation.layout.fillMaxSize
import com.example.data.network.HttpClient
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JSlider
import javax.swing.SwingConstants
import javax.swing.Timer
import javax.swing.BorderFactory

/** Track information returned by LibVLC after the media metadata becomes available. */
private data class VlcTrack(val id: Int, val label: String)

/**
 * LibVLC renders through a heavyweight native Swing component. For that reason the
 * controls live in the same Swing hierarchy, rather than in a Compose overlay that
 * would be hidden behind the video surface on Windows.
 */
@Composable
fun EmbeddedVlcPlayer(
    url: String,
    title: String,
    headers: Map<String, String>,
    resumeAtMillis: Long,
    onProgress: (Long) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
    hasNext: Boolean,
) {
    val latestBack by rememberUpdatedState(onBack)
    val latestNext by rememberUpdatedState(onNext)
    val latestProgress by rememberUpdatedState(onProgress)
    val player = remember { NativeVlcPlayer({ latestBack() }, { latestNext() }, { position -> latestProgress(position) }) }

    DisposableEffect(player) { onDispose { player.release() } }
    LaunchedEffect(url, title, headers, hasNext, resumeAtMillis) { player.play(url, title, headers, hasNext, resumeAtMillis) }
    SwingPanel(factory = { player.root }, modifier = Modifier.fillMaxSize())
}

/** A native player frame with transport and track controls that accept real mouse input. */
private class NativeVlcPlayer(
    private val onBack: () -> Unit,
    private val onNext: () -> Unit,
    private val onProgress: (Long) -> Unit,
) {
    private val media = EmbeddedMediaPlayerComponent("--network-caching=1800", "--http-reconnect", "--no-video-title-show")
    private val title = JLabel("", SwingConstants.LEFT).apply { foreground = Color(0xF3, 0xF5, 0xF8) }
    private val playPause = darkButton("Pausa")
    private val time = JLabel("00:00 / 00:00").apply { foreground = Color(0xD8, 0xDE, 0xE9) }
    private val seek = JSlider(0, 1000, 0)
    private val next = darkButton("Prossimo episodio")
    private var seeking = false
    private var duration = 0L
    private var pendingResumeAt = 0L
    private var lastReportedPosition = 0L

    val root: JPanel = JPanel(BorderLayout()).apply {
        background = Color.BLACK
        add(media, BorderLayout.CENTER)
        add(createControls(), BorderLayout.SOUTH)
    }

    /** Polls LibVLC only to refresh visible transport state; it never blocks the UI. */
    private val clock = Timer(400) {
        duration = media.mediaPlayer().status().length().coerceAtLeast(0)
        val position = media.mediaPlayer().status().time().coerceAtLeast(0)
        val seekable = media.mediaPlayer().status().isSeekable
        seek.isEnabled = seekable
        seek.toolTipText = if (seekable) "Trascina per cambiare punto" else "Questo flusso non permette lo spostamento"
        if (pendingResumeAt > 0 && seekable && duration > 0) {
            media.mediaPlayer().controls().setPosition((pendingResumeAt.toFloat() / duration).coerceIn(0f, 0.995f))
            pendingResumeAt = 0L
        }
        if (!seeking && duration > 0) seek.value = ((position * 1000) / duration).toInt()
        time.text = "${format(position)} / ${format(duration)}"
        playPause.text = if (media.mediaPlayer().status().isPlaying) "Pausa" else "Riprendi"
        if (position > 0 && kotlin.math.abs(position - lastReportedPosition) >= 3_000L) {
            lastReportedPosition = position
            onProgress(position)
        }
    }

    private fun createControls(): JPanel = JPanel(BorderLayout()).apply {
        background = Color(24, 28, 37)
        border = BorderFactory.createEmptyBorder(9, 14, 10, 14)
        val actions = JPanel(FlowLayout(FlowLayout.LEFT, 8, 7)).apply { background = Color(24, 28, 37) }
        val back = darkButton("Indietro")
        val audio = darkButton("Audio")
        val subtitles = darkButton("Sottotitoli")
        back.addActionListener { onBack() }
        playPause.addActionListener {
            if (media.mediaPlayer().status().isPlaying) media.mediaPlayer().controls().pause()
            else media.mediaPlayer().controls().play()
        }
        audio.addActionListener { showAudioMenu(audio) }
        subtitles.addActionListener { showSubtitleMenu(subtitles) }
        next.addActionListener { onNext() }
        seek.addChangeListener {
            seeking = seek.valueIsAdjusting
            if (!seeking && duration > 0 && seek.isEnabled) {
                // LibVLC's relative position is more reliable than setTime for HLS streams.
                media.mediaPlayer().controls().setPosition(seek.value / 1000f)
            }
        }
        actions.add(back); actions.add(playPause); actions.add(audio); actions.add(subtitles); actions.add(next); actions.add(time)
        add(title, BorderLayout.NORTH)
        add(seek, BorderLayout.CENTER)
        add(actions, BorderLayout.SOUTH)
    }

    /** Rebuild menus at click time because VLC exposes tracks after playback begins. */
    private fun showAudioMenu(anchor: JButton) {
        val tracks = media.mediaPlayer().audio().trackDescriptions().map { VlcTrack(it.id(), it.description()) }
        trackMenu(anchor, tracks) { id -> media.mediaPlayer().audio().setTrack(id) }
    }

    /** Includes an explicit off action, making subtitles disabled by default and on demand. */
    private fun showSubtitleMenu(anchor: JButton) {
        val popup = JPopupMenu()
        popup.add(JMenuItem("Disattivati").apply { addActionListener { media.mediaPlayer().subpictures().setTrack(-1) } })
        media.mediaPlayer().subpictures().trackDescriptions().forEach { track ->
            popup.add(JMenuItem(track.description()).apply { addActionListener { media.mediaPlayer().subpictures().setTrack(track.id()) } })
        }
        popup.show(anchor, 0, anchor.height)
    }

    private fun trackMenu(anchor: JButton, tracks: List<VlcTrack>, select: (Int) -> Unit) {
        val popup = JPopupMenu()
        if (tracks.isEmpty()) popup.add(JMenuItem("Nessuna traccia disponibile"))
        tracks.forEach { track -> popup.add(JMenuItem(track.label).apply { addActionListener { select(track.id) } }) }
        popup.show(anchor, 0, anchor.height)
    }

    /** Applies one consistent dark, high-contrast style to every native player action. */
    private fun darkButton(label: String): JButton = JButton(label).apply {
        foreground = Color(0xF3, 0xF5, 0xF8)
        background = Color(0x2A, 0x30, 0x3B)
        isFocusPainted = false
        isBorderPainted = false
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        preferredSize = Dimension(132, 32)
    }

    /** Starts a new source and intentionally disables subtitle rendering for it. */
    fun play(url: String, label: String, headers: Map<String, String>, hasNext: Boolean, resumeAtMillis: Long) {
        title.text = label
        next.isEnabled = hasNext
        pendingResumeAt = resumeAtMillis
        lastReportedPosition = 0L
        media.mediaPlayer().media().play(
            url,
            ":http-user-agent=${headers["User-Agent"] ?: HttpClient.USER_AGENT}",
            *headers["Referer"]?.let { arrayOf(":http-referrer=$it") }.orEmpty(),
        )
        // VLC uses -1 as the no-subpicture-track value.
        media.mediaPlayer().subpictures().setTrack(-1)
        if (!clock.isRunning) clock.start()
    }

    fun release() {
        // Save even a very recent pause that did not reach the periodic 3-second checkpoint.
        media.mediaPlayer().status().time().takeIf { it > 0 }?.let(onProgress)
        clock.stop()
        media.release()
    }
}

/** Formats milliseconds from LibVLC for the native player timeline. */
private fun format(milliseconds: Long): String {
    val seconds = milliseconds / 1000
    return "%02d:%02d".format(seconds / 60, seconds % 60)
}
