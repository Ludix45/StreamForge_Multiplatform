package com.aistudio.streamforge.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.EventQueue
import java.awt.FlowLayout
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.Box
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JSlider
import javax.swing.ListCellRenderer
import javax.swing.Timer
import javax.swing.border.EmptyBorder
import javax.swing.plaf.basic.BasicSliderUI

/**
 * Real embedded MPV implementation. Unlike `mpv.exe --wid`, libmpv runs inside
 * this JVM and accepts commands directly, so it cannot take over the app window.
 */
@Composable
fun EmbeddedLibMpvPlayer(
    url: String,
    title: String,
    headers: Map<String, String>,
    resumeAtMillis: Long,
    onProgress: (Long) -> Unit,
    onUnavailable: (String) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
    hasNext: Boolean,
) {
    val latestBack by rememberUpdatedState(onBack)
    val latestNext by rememberUpdatedState(onNext)
    val latestProgress by rememberUpdatedState(onProgress)
    val latestUnavailable by rememberUpdatedState(onUnavailable)
    val player = remember {
        LibMpvHost({ latestBack() }, { latestNext() }, { latestProgress(it) }, { latestUnavailable(it) })
    }
    DisposableEffect(player) { onDispose { player.release() } }
    LaunchedEffect(url, title, headers, resumeAtMillis, hasNext) {
        player.open(url, title, headers, resumeAtMillis, hasNext)
    }
    SwingPanel(factory = { player.root }, modifier = Modifier.fillMaxSize())
}

/** Small JNA mapping of the public libmpv C API used by this player. */
private interface LibMpvApi : Library {
    fun mpv_create(): Pointer?
    fun mpv_initialize(context: Pointer): Int
    fun mpv_set_option_string(context: Pointer, name: String, value: String): Int
    fun mpv_command_string(context: Pointer, command: String): Int
    fun mpv_get_property_string(context: Pointer, name: String): Pointer?
    fun mpv_free(data: Pointer)
    fun mpv_terminate_destroy(context: Pointer)
}

/** Player host with a heavyweight controller canvas that stays clickable on Windows. */
private class LibMpvHost(
    private val onBack: () -> Unit,
    private val onNext: () -> Unit,
    private val onProgress: (Long) -> Unit,
    private val onUnavailable: (String) -> Unit,
) {
    private val video = MpvCanvas()
    private var api: LibMpvApi? = null
    private var handle: Pointer? = null
    private var durationMs = 0L
    private var positionMs = 0L
    private var lastSavedMs = 0L
    private var resumeAtMs = 0L
    private var paused = false
    private var muted = false
    private var audioTracks = mutableListOf<TrackInfo>()
    private var subtitleTracks = mutableListOf<TrackInfo>()
    
    @Volatile private var status = "Preparazione player…"
    private val released = AtomicBoolean(false)

    private lateinit var controls: JPanel
    private lateinit var playPauseBtn: JButton
    private lateinit var muteBtn: JButton
    private lateinit var volumeSlider: JSlider
    private lateinit var timeLabel: JLabel
    private lateinit var audioCombo: JComboBox<String>
    private lateinit var subsCombo: JComboBox<String>
    private lateinit var seekSlider: JSlider

    val root: JPanel = JPanel(BorderLayout()).apply {
        background = Color.BLACK
        add(video, BorderLayout.CENTER)
    }

    private fun createControls(): JPanel {
        val panel = JPanel(BorderLayout()).apply {
            background = Color(0x10, 0x12, 0x18)
            border = EmptyBorder(8, 16, 8, 16)
        }

        // Seek Bar (Top)
        seekSlider = JSlider(0, 1000, 0).apply {
            background = Color(0x10, 0x12, 0x18)
            foreground = Color(0xFF, 0x79, 0x00)
            setUI(BasicSliderUI(this))
            addMouseListener(object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent) {
                    val value = (e.x.toDouble() / width * maximum).toInt()
                    this@apply.value = value
                    seekToFraction(value / 1000f)
                }
            })
        }
        panel.add(seekSlider, BorderLayout.NORTH)

        val bottomRow = JPanel(FlowLayout(FlowLayout.LEFT, 12, 0)).apply {
            background = Color(0x10, 0x12, 0x18)
        }

        // Play/Pause
        playPauseBtn = playerButton("▶") {
            paused = !paused
            command("cycle pause")
            updateUI()
        }
        bottomRow.add(playPauseBtn)

        // Indietro
        bottomRow.add(playerButton("←") { onBack() })

        // Prossimo
        bottomRow.add(playerButton("⏭") { onNext() })

        // Time Label
        timeLabel = JLabel("00:00 / 00:00").apply {
            foreground = Color(0xEE, 0xF2, 0xF8)
            font = font.deriveFont(12f)
        }
        bottomRow.add(timeLabel)

        bottomRow.add(Box.createHorizontalStrut(20))

        // Mute
        muteBtn = playerButton("🔊") {
            muted = !muted
            command("cycle mute")
            updateUI()
        }
        bottomRow.add(muteBtn)

        // Volume Slider
        volumeSlider = JSlider(0, 100, 100).apply {
            preferredSize = Dimension(100, 20)
            background = Color(0x10, 0x12, 0x18)
            foreground = Color(0xFF, 0x79, 0x00)
            addChangeListener {
                command("set volume $value")
            }
        }
        bottomRow.add(volumeSlider)

        bottomRow.add(Box.createHorizontalStrut(20))

        val darkRenderer = object : ListCellRenderer<String> {
            private val label = JLabel().apply {
                isOpaque = true
                border = EmptyBorder(4, 8, 4, 8)
            }
            override fun getListCellRendererComponent(list: JList<out String>?, value: String?, index: Int, isSelected: Boolean, cellHasFocus: Boolean): Component {
                label.text = value
                if (isSelected) {
                    label.background = Color(0xFF, 0x79, 0x00)
                    label.foreground = Color.BLACK
                } else {
                    label.background = Color(0x29, 0x31, 0x3E)
                    label.foreground = Color(0xF3, 0xF5, 0xF8)
                }
                return label
            }
        }

        // Audio Dropdown
        audioCombo = JComboBox<String>(arrayOf("Caricamento tracce...")).apply {
            background = Color(0x29, 0x31, 0x3E)
            foreground = Color(0xF3, 0xF5, 0xF8)
            renderer = darkRenderer
            addActionListener {
                if (hasFocus()) {
                    val idx = selectedIndex
                    if (idx >= 0 && idx < audioTracks.size) {
                        command("set audio ${audioTracks[idx].id}")
                    }
                }
            }
        }
        bottomRow.add(audioCombo)

        // Subtitles Dropdown
        subsCombo = JComboBox<String>(arrayOf("Sottotitoli: Off")).apply {
            background = Color(0x29, 0x31, 0x3E)
            foreground = Color(0xF3, 0xF5, 0xF8)
            renderer = darkRenderer
            addActionListener {
                if (hasFocus()) {
                    val idx = selectedIndex
                    if (idx == 0) {
                        command("set sub-visibility no")
                        command("set sid no")
                    } else if (idx > 0 && idx <= subtitleTracks.size) {
                        command("set sub-visibility yes")
                        command("set sid ${subtitleTracks[idx - 1].id}")
                    }
                }
            }
        }
        bottomRow.add(subsCombo)

        bottomRow.add(Box.createHorizontalGlue())

        // Chiudi
        bottomRow.add(playerButton("✕") { onBack() })

        panel.add(bottomRow, BorderLayout.CENTER)
        return panel
    }

    private fun playerButton(icon: String, onClick: () -> Unit) = JButton(icon).apply {
        background = Color(0x29, 0x31, 0x3E)
        foreground = Color(0xF3, 0xF5, 0xF8)
        isBorderPainted = false
        isFocusPainted = false
        font = font.deriveFont(Font.PLAIN, 16f)
        addActionListener { onClick() }
    }

    private fun seekToFraction(fraction: Float) {
        if (durationMs > 0) command("seek ${(durationMs.toFloat() * fraction / 1000f)} absolute")
    }

    private fun updateUI() {
        EventQueue.invokeLater {
            playPauseBtn.text = if (paused) "▶" else "Ⅱ"
            muteBtn.text = if (muted) "🔇" else "🔊"
            timeLabel.text = "${formatMpv(positionMs)} / ${formatMpv(durationMs)}"
            if (durationMs > 0) {
                seekSlider.value = ((positionMs.toFloat() / durationMs.toFloat()) * 1000).toInt()
            }
        }
    }

    private var lastTracksUpdate = 0L
    private fun syncTracks() {
        val now = System.currentTimeMillis()
        if (now - lastTracksUpdate < 2000) return
        lastTracksUpdate = now
        
        Thread {
            val trackCount = property("track-list/count")?.toIntOrNull() ?: 0
            val newAudio = mutableListOf<TrackInfo>()
            val newSubs = mutableListOf<TrackInfo>()
            
            for (i in 0 until trackCount) {
                val type = property("track-list/$i/type")
                val id = property("track-list/$i/id")?.toIntOrNull() ?: continue
                val lang = property("track-list/$i/lang")
                val title = property("track-list/$i/title") ?: lang ?: "Track $id"
                
                when (type) {
                    "audio" -> newAudio.add(TrackInfo(id, title, lang))
                    "sub" -> newSubs.add(TrackInfo(id, title, lang))
                }
            }
            
            if (newAudio != audioTracks || newSubs != subtitleTracks) {
                audioTracks = newAudio
                subtitleTracks = newSubs
                EventQueue.invokeLater {
                    val audioLabels = audioTracks.map { "Audio: ${it.title}" }.toTypedArray()
                    audioCombo.model = DefaultComboBoxModel(if (audioLabels.isEmpty()) arrayOf("Audio: N/A") else audioLabels)
                    
                    val subLabels = mutableListOf("Sottotitoli: Off")
                    subLabels.addAll(subtitleTracks.map { "Sub: ${it.title}" })
                    subsCombo.model = DefaultComboBoxModel(subLabels.toTypedArray())
                }
            }
        }.start()
    }

    private val progressTimer = Timer(500) {
        positionMs = property("time-pos")?.toDoubleOrNull()?.times(1000)?.toLong() ?: positionMs
        durationMs = property("duration")?.toDoubleOrNull()?.times(1000)?.toLong() ?: durationMs
        property("pause")?.let { paused = it == "yes" }
        property("mute")?.let { muted = it == "yes" }
        
        if (resumeAtMs > 0 && durationMs > 0) {
            command("seek ${resumeAtMs / 1000.0} absolute")
            resumeAtMs = 0L
        }
        if (positionMs > 0 && kotlin.math.abs(positionMs - lastSavedMs) >= 3_000L) {
            lastSavedMs = positionMs
            onProgress(positionMs)
        }
        updateUI()
        syncTracks()
    }

    init {
        controls = createControls()
        root.add(controls, BorderLayout.SOUTH)
        video.onPeerReady = { /* Handled in open() */ }
    }

    /** Opens one stream using an in-process libmpv context, never an external mpv.exe process. */
    fun open(url: String, title: String, headers: Map<String, String>, resumeAtMillis: Long, hasNext: Boolean) {
        if (released.get()) return
        resumeAtMs = resumeAtMillis.coerceAtLeast(0)
        status = "Caricamento MPV integrato…"
        Thread({
            val library = loadLibMpv()
            if (library == null) {
                val os = System.getProperty("os.name").lowercase()
                val isWin = os.contains("win")
                val isMac = os.contains("mac")
                
                val missingFile = when {
                    isWin -> "libmpv-2.dll"
                    isMac -> "libmpv.2.dylib"
                    else -> "libmpv.so.2"
                }

                val installCmd = when {
                    isMac -> "brew install mpv"
                    os.contains("linux") -> {
                        // Proviamo a suggerire apt come default per le distro comuni
                        "sudo apt update && sudo apt install libmpv2"
                    }
                    else -> null
                }

                val sb = StringBuilder()
                sb.append("<html><div style='text-align: center; color: #F3F5F8; font-family: sans-serif;'>")
                sb.append("<h2 style='color: #FF7900;'>Componente di riproduzione mancante</h2>")
                sb.append("<p>Per riprodurre i contenuti, StreamForge ha bisogno della libreria <b>$missingFile</b>.</p>")
                
                if (installCmd != null) {
                    sb.append("<p>Puoi installarla rapidamente aprendo il terminale e scrivendo:</p>")
                    sb.append("<code style='background: #29313E; padding: 4px 8px; border-radius: 4px; color: #3DDC84;'>$installCmd</code>")
                }
                
                sb.append("<p style='margin-top: 20px;'>Per maggiori informazioni, visita il sito ufficiale:</p>")
                sb.append("<a href='https://mpv.io/installation/' style='color: #3DDC84;'>mpv.io/installation</a>")
                sb.append("</div></html>")

                EventQueue.invokeLater { onUnavailable(sb.toString()) }
                return@Thread
            }
            val context = library.mpv_create()
            if (context == null) {
                EventQueue.invokeLater { onUnavailable("libmpv non ha creato il player.") }
                return@Thread
            }
            val hwnd = runCatching { Pointer.nativeValue(Native.getComponentPointer(video)) }.getOrNull()
            if (hwnd == null || released.get()) {
                library.mpv_terminate_destroy(context)
                return@Thread
            }
            // All options must be set before mpv_initialize.
            library.mpv_set_option_string(context, "wid", hwnd.toString())
            library.mpv_set_option_string(context, "config", "no")
            library.mpv_set_option_string(context, "force-window", "yes")
            library.mpv_set_option_string(context, "osc", "no")
            library.mpv_set_option_string(context, "cache", "yes")
            library.mpv_set_option_string(context, "cache-secs", "20")
            library.mpv_set_option_string(context, "network-timeout", "30")
            library.mpv_set_option_string(context, "sub-visibility", "no")
            library.mpv_set_option_string(context, "alang", "ita,it,eng,en")
            headers["User-Agent"]?.let { library.mpv_set_option_string(context, "user-agent", it) }
            headers["Referer"]?.let { library.mpv_set_option_string(context, "referrer", it) }
            if (library.mpv_initialize(context) < 0) {
                library.mpv_terminate_destroy(context)
                EventQueue.invokeLater { onUnavailable("libmpv non ha inizializzato il video.") }
                return@Thread
            }
            synchronized(this) { api = library; handle = context }
            command("loadfile \"${url.replace("\\", "\\\\").replace("\"", "\\\"")}\" replace")
            EventQueue.invokeLater { if (!progressTimer.isRunning) progressTimer.start(); updateUI() }
        }, "streamforge-libmpv-start").apply { isDaemon = true; start() }
    }

    /** Executes an MPV command against the current in-process context. */
    private fun command(value: String) {
        synchronized(this) { api?.let { currentApi -> handle?.let { currentApi.mpv_command_string(it, value) } } }
    }

    /** Reads a simple string property and releases MPV-owned memory immediately. */
    private fun property(name: String): String? = synchronized(this) {
        val currentApi = api ?: return@synchronized null
        val value = handle?.let { currentApi.mpv_get_property_string(it, name) } ?: return@synchronized null
        try { value.getString(0) } finally { currentApi.mpv_free(value) }
    }

    fun release() {
        if (!released.compareAndSet(false, true)) return
        progressTimer.stop()
        positionMs.takeIf { it > 0 }?.let(onProgress)
        synchronized(this) {
            handle?.let { current -> api?.mpv_terminate_destroy(current) }
            handle = null; api = null
        }
    }

    private data class TrackInfo(val id: Int, val title: String, val lang: String?)
}

/** Locates the bundled libmpv runtime without copying Nuvio source code or its bridge. */
private fun loadLibMpv(): LibMpvApi? {
    val os = System.getProperty("os.name").lowercase()
    val isWin = os.contains("win")
    val isMac = os.contains("mac")
    val libName = when {
        isWin -> "libmpv-2.dll"
        isMac -> "libmpv.2.dylib"
        else -> "libmpv.so.2"
    }
    
    val override = System.getenv("STREAMFORGE_LIBMPV_PATH")?.let(::File)
    
    // 1. Official Compose Desktop resources directory
    val composeResourcesDir = System.getProperty("compose.application.resources.dir")?.let { File(it) }
    val bundledLib = composeResourcesDir?.resolve(libName)
    
    // 2. Check in the same directory as the JAR (Standard for jpackage)
    val appDir = File(System.getProperty("user.dir"), "app")
    val jpackageLib = File(appDir, libName)
    
    // 3. Check in the resources directory (Alternative)
    val resourcesLib = File(appDir, "resources/$libName")
    
    // 4. Fallback for development (Relative to reference project)
    val localRuntime = if (isWin) {
        generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .map { it.resolve("NuvioDesktop-reference/composeApp/src/desktopMain/native/windows/runtime/libmpv-2.dll") }
            .firstOrNull(File::isFile)
    } else null
        
    // 5. Fallback for development in current project resources
    val currentResources = File("desktopApp/src/desktopMain/resources/$libName")
        
    val libFile = listOfNotNull(override, bundledLib, jpackageLib, resourcesLib, localRuntime, currentResources).firstOrNull(File::isFile)
    
    return try {
        if (libFile != null) {
            Native.load(libFile.absolutePath, LibMpvApi::class.java)
        } else {
            // Last resort: try to load from system path
            val sysName = when {
                isWin -> "mpv-2"
                isMac -> "mpv.2"
                else -> "mpv"
            }
            Native.load(sysName, LibMpvApi::class.java)
        }
    } catch (e: Exception) {
        null
    }
}
