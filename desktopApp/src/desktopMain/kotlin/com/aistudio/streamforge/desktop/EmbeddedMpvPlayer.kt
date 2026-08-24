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
import com.example.data.network.HttpClient
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import java.awt.BorderLayout
import java.awt.Canvas
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.EventQueue
import java.awt.FlowLayout
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
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
 * MPV executable embedded in an AWT Canvas through --wid. Unlike the prior VLC
 * surface, MPV owns decoding and buffering while the app owns a reliable control bar.
 */
@Composable
fun EmbeddedMpvPlayer(
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
    val player = remember { EmbeddedMpvHost({ latestBack() }, { latestNext() }, { latestProgress(it) }, { message -> latestUnavailable(message) }) }

    DisposableEffect(player) { onDispose { player.release() } }
    LaunchedEffect(url, title, headers, resumeAtMillis, hasNext) { player.open(url, title, headers, resumeAtMillis, hasNext) }
    SwingPanel(factory = { player.root }, modifier = Modifier.fillMaxSize())
}

/** Canvas becomes displayable before MPV is started, avoiding the blank --wid window race. */
internal class MpvCanvas : Canvas() {
    var onPeerReady: (() -> Unit)? = null

    init { background = Color.BLACK; ignoreRepaint = false }

    override fun addNotify() {
        super.addNotify()
        EventQueue.invokeLater { onPeerReady?.invoke() }
    }
}

/**
 * A heavyweight AWT control surface. It deliberately is not a Swing/Compose overlay:
 * a native MPV Canvas can otherwise paint over lightweight controls on Windows.
 */
private class EmbeddedMpvHost(
    private val onBack: () -> Unit,
    private val onNext: () -> Unit,
    private val onProgress: (Long) -> Unit,
    private val onUnavailable: (String) -> Unit,
) {
    private val video = MpvCanvas()
    private var process: Process? = null
    /** Native Windows pipe handle used by MPV's documented JSON IPC protocol. */
    private var ipcHandle: Pointer? = null
    private var pending: PendingSource? = null
    private var durationMs = 0L
    private var positionMs = 0L
    private var lastSavedMs = 0L
    private var resumePendingMs = 0L
    private var userScrubbing = false
    private var paused = false
    private var muted = false
    private val released = AtomicBoolean(false)
    private data class TrackInfo(val id: Int, val title: String, val lang: String?)
    private var audioTracks = mutableListOf<TrackInfo>()
    private var subtitleTracks = mutableListOf<TrackInfo>()

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

        seekSlider = JSlider(0, 1000, 0).apply {
            background = Color(0x10, 0x12, 0x18)
            foreground = Color(0xFF, 0x79, 0x00)
            setUI(BasicSliderUI(this))
            addMouseListener(object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent) {
                    val value = (e.x.toDouble() / width * maximum).toInt()
                    this@apply.value = value
                    if (durationMs > 0) send("seek ${(durationMs * value / 1000L) / 1000.0} absolute")
                }
            })
        }
        panel.add(seekSlider, BorderLayout.NORTH)

        val bottomRow = JPanel(FlowLayout(FlowLayout.LEFT, 12, 0)).apply {
            background = Color(0x10, 0x12, 0x18)
        }

        playPauseBtn = playerButton("▶") { togglePause() }
        bottomRow.add(playPauseBtn)
        bottomRow.add(playerButton("←") { onBack() })
        bottomRow.add(playerButton("⏭") { onNext() })

        timeLabel = JLabel("00:00 / 00:00").apply {
            foreground = Color(0xEE, 0xF2, 0xF8)
            font = font.deriveFont(12f)
        }
        bottomRow.add(timeLabel)
        bottomRow.add(Box.createHorizontalStrut(20))

        muteBtn = playerButton("🔊") { send("cycle mute"); muted = !muted; updateUI() }
        bottomRow.add(muteBtn)

        volumeSlider = JSlider(0, 100, 100).apply {
            preferredSize = Dimension(100, 20)
            background = Color(0x10, 0x12, 0x18)
            foreground = Color(0xFF, 0x79, 0x00)
            addChangeListener { if (!valueIsAdjusting) send("set volume $value") }
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

        audioCombo = JComboBox<String>(arrayOf("Audio: Italiano")).apply {
            background = Color(0x29, 0x31, 0x3E)
            foreground = Color(0xF3, 0xF5, 0xF8)
            renderer = darkRenderer
            addActionListener { 
                if (hasFocus()) {
                    val idx = selectedIndex
                    if (idx >= 0 && idx < audioTracks.size) {
                        send("set audio ${audioTracks[idx].id}")
                    }
                }
            }
        }
        bottomRow.add(audioCombo)

        subsCombo = JComboBox<String>(arrayOf("Sottotitoli: Off")).apply {
            background = Color(0x29, 0x31, 0x3E)
            foreground = Color(0xF3, 0xF5, 0xF8)
            renderer = darkRenderer
            addActionListener { 
                if (hasFocus()) {
                    val idx = selectedIndex
                    if (idx == 0) {
                        send("set sub-visibility no")
                        send("set sid no")
                    } else if (idx > 0 && idx <= subtitleTracks.size) {
                        send("set sub-visibility yes")
                        send("set sid ${subtitleTracks[idx - 1].id}")
                    }
                }
            }
        }
        /*
        bottomRow.add(subsCombo)

        bottomRow.add(Box.createHorizontalGlue())
        bottomRow.add(playerButton("✕") { onBack() })

        panel.add(bottomRow, BorderLayout.CENTER)
        */
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

    private fun updateUI() {
        EventQueue.invokeLater {
            playPauseBtn.text = if (paused) "▶" else "❚❚"
            muteBtn.text = if (muted) "🔇" else "🔊"
            timeLabel.text = "${formatMpv(positionMs)} / ${formatMpv(durationMs)}"
            if (durationMs > 0 && !userScrubbing) {
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
            // Need to send get_property via IPC and wait for response, simplified for now
            // In standalone MPV, we would query and parse JSON responses for track-list
        }.start()
    }

    private val poller = Timer(450) {
        send("get_property time-pos")
        send("get_property duration")
        send("get_property pause")
        send("get_property mute")
        
        if (positionMs > 0 && kotlin.math.abs(positionMs - lastSavedMs) >= 3_000L) {
            lastSavedMs = positionMs
            onProgress(positionMs)
        }
        if (resumePendingMs > 0 && durationMs > 0) {
            send("seek ${resumePendingMs / 1000.0} absolute")
            resumePendingMs = 0L
        }
        updateUI()
        syncTracks()
    }

    init {
        root.add(createControls(), BorderLayout.SOUTH)
        video.onPeerReady = { pending?.let(::launchOnCanvas) }
    }

    private fun togglePause() {
        send("cycle pause")
        paused = !paused
        updateUI()
    }

    fun open(url: String, label: String, headers: Map<String, String>, resumeAtMillis: Long, hasNext: Boolean) {
        resumePendingMs = resumeAtMillis.coerceAtLeast(0)
        pending = PendingSource(url, headers)
        if (video.isDisplayable) launchOnCanvas(pending!!)
    }

    /** Starts MPV only after the Canvas has an HWND; this is essential for --wid on Windows. */
    private fun launchOnCanvas(source: PendingSource) {
        if (released.get()) return
        stopProcess()
        val hwnd = runCatching { Pointer.nativeValue(Native.getComponentPointer(video)) }.getOrNull()
            ?: run { onUnavailable("Impossibile ottenere la superficie Windows per MPV."); return }
        val executable = findMpvExecutable()
            ?: run { onUnavailable("mpv.exe non è stato trovato."); return }
        // A unique name prevents collisions when a user opens a second player window.
        val pipeName = "\\\\.\\pipe\\streamforge-mpv-${System.nanoTime()}"
        val command = listOf(
            executable,
            "--wid=$hwnd",
            "--force-window=immediate",
            "--keep-open=yes",
            "--osc=no",
            "--osd-level=1",
            "--input-ipc-server=$pipeName",
            "--input-terminal=no",
            "--terminal=yes",
            "--msg-level=all=warn",
            "--cache=yes",
            "--cache-secs=20",
            "--network-timeout=30",
            "--sub-visibility=no",
            "--alang=ita,it,eng,en",
        ) + listOfNotNull(
            source.headers["User-Agent"]?.let { "--user-agent=$it" },
            source.headers["Referer"]?.let { "--referrer=$it" },
        ) + source.headers.filterKeys { it != "User-Agent" && it != "Referer" }
            .entries.map { (name, value) -> "--http-header-fields=$name: $value" } + source.url
        runCatching {
            ProcessBuilder(command).redirectErrorStream(true).start()
        }.onSuccess { started ->
            process = started
            connectIpc(started, pipeName)
            if (!poller.isRunning) poller.start()
            Thread {
                val exitCode = runCatching { started.waitFor() }.getOrDefault(0)
                if (!released.get() && process === started) {
                    EventQueue.invokeLater { onBack() }
                }
            }.apply { isDaemon = true; name = "streamforge-mpv-exit"; start() }
        }.onFailure { error ->
            onUnavailable("MPV non è stato avviato: ${error.message ?: "errore sconosciuto"}")
        }
    }

    /** Opens MPV's JSON IPC pipe and continuously receives property query responses. */
    private fun connectIpc(started: Process, pipeName: String) {
        Thread({
            repeat(40) {
                if (released.get() || process !== started) return@Thread
                val handle = openNativePipe(pipeName)
                if (handle != null) {
                    synchronized(this) { if (process === started) ipcHandle = handle else closeNativePipe(handle) }
                    readIpcResponses(handle, started)
                    return@Thread
                }
                Thread.sleep(125)
            }
        }, "streamforge-mpv-ipc").apply { isDaemon = true; start() }
    }

    /** Responses are identified by request id: 1 is time-pos, 2 is duration. */
    private fun readIpcResponses(handle: Pointer, started: Process) {
        val pendingJson = StringBuilder()
        runCatching {
            while (!released.get() && process === started) {
                val chunk = readNativePipe(handle) ?: break
                pendingJson.append(chunk)
                while (true) {
                    val breakAt = pendingJson.indexOf("\n")
                    if (breakAt < 0) break
                    val line = pendingJson.substring(0, breakAt)
                    pendingJson.delete(0, breakAt + 1)
                    val value = Regex("\"data\"\\s*:\\s*(-?[0-9.]+|true|false|yes|no)").find(line)?.groupValues?.get(1) ?: continue
                    when {
                        line.contains("\"request_id\":1") -> positionMs = (value.toDoubleOrNull() ?: 0.0).times(1000).toLong()
                        line.contains("\"request_id\":2") -> durationMs = (value.toDoubleOrNull() ?: 0.0).times(1000).toLong()
                        line.contains("\"request_id\":3") -> paused = value == "true" || value == "yes"
                        line.contains("\"request_id\":4") -> muted = value == "true" || value == "yes"
                    }
                }
            }
        }
        synchronized(this) { if (ipcHandle === handle) ipcHandle = null }
        closeNativePipe(handle)
    }

    /** Sends a JSON IPC command. MPV accepts numeric seek arguments as JSON numbers. */
    private fun send(command: String) {
        val arguments = command.trim().split(Regex("\\s+")).filter(String::isNotBlank)
        if (arguments.isEmpty()) return
        val requestId = when (arguments.joinToString(" ")) {
            "get_property time-pos" -> 1
            "get_property duration" -> 2
            "get_property pause" -> 3
            "get_property mute" -> 4
            else -> 0
        }
        val jsonArguments = arguments.joinToString(",") { argument ->
            if (argument.toDoubleOrNull() != null) argument else "\"${argument.replace("\"", "\\\"")}\""
        }
        val payload = "{\"command\":[${jsonArguments}],\"request_id\":$requestId}\n"
        synchronized(this) {
            ipcHandle?.let { handle -> writeNativePipe(handle, payload.toByteArray(Charsets.UTF_8)) }
        }
    }

    private fun stopProcess() {
        positionMs.takeIf { it > 0 }?.let(onProgress)
        poller.stop()
        runCatching { send("quit") }
        process?.destroy()
        process = null
        ipcHandle?.let(::closeNativePipe)
        ipcHandle = null
    }

    fun release() { released.set(true); stopProcess() }

    private data class PendingSource(val url: String, val headers: Map<String, String>)
}

/** Finds MPV from an explicit environment override, PATH, or the Windows winget package. */
private fun findMpvExecutable(): String? {
    System.getenv("STREAMFORGE_MPV_PATH")?.takeIf { File(it).isFile }?.let { return it }
    val localAppData = System.getenv("LOCALAPPDATA") ?: return "mpv"
    val wingetRoot = File(localAppData, "Microsoft/WinGet/Packages")
    wingetRoot.listFiles()?.firstOrNull { it.name.startsWith("mpv-player.mpv-", true) }
        ?.resolve("mpv.exe")?.takeIf(File::isFile)?.let { return it.absolutePath }
    return "mpv"
}

internal fun formatMpv(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1000
    return "%02d:%02d".format(seconds / 60, seconds % 60)
}

/** Minimal User32 declaration: no additional Windows-only library is required. */
private interface User32Messages : Library {
    fun PostMessageW(window: Pointer, message: Int, key: Int, lParam: Int): Boolean
}

private val user32Messages: User32Messages? = runCatching {
    Native.load("user32", User32Messages::class.java)
}.getOrNull()

/** Posts key down/up messages to MPV's child HWND, as a real keyboard would. */
private fun nativeKey(window: Pointer, key: Int, down: Boolean) {
    user32Messages?.PostMessageW(window, if (down) 0x0100 else 0x0101, key, 0)
}

/** Direct Win32 named-pipe calls avoid Java's unreliable RandomAccessFile pipe handling. */
private interface Kernel32Pipes : StdCallLibrary {
    fun WaitNamedPipeW(name: WString, timeout: Int): Boolean
    fun CreateFileW(name: WString, access: Int, share: Int, security: Pointer?, creation: Int, flags: Int, template: Pointer?): Pointer
    fun WriteFile(handle: Pointer, buffer: ByteArray, size: Int, written: IntByReference, overlapped: Pointer?): Boolean
    fun ReadFile(handle: Pointer, buffer: ByteArray, size: Int, read: IntByReference, overlapped: Pointer?): Boolean
    fun CloseHandle(handle: Pointer): Boolean
}

private val kernel32Pipes: Kernel32Pipes? = runCatching {
    Native.load("kernel32", Kernel32Pipes::class.java)
}.getOrNull()

private const val GENERIC_READ = -2147483648
private const val GENERIC_WRITE = 0x40000000
private const val OPEN_EXISTING = 3

/** Opens the server created by `--input-ipc-server`; null means MPV has not exposed it yet. */
private fun openNativePipe(path: String): Pointer? {
    val api = kernel32Pipes ?: return null
    if (!api.WaitNamedPipeW(WString(path), 250)) return null
    val handle = api.CreateFileW(WString(path), GENERIC_READ or GENERIC_WRITE, 0, null, OPEN_EXISTING, 0, null)
    return handle?.takeUnless { Pointer.nativeValue(it) == -1L }
}

private fun writeNativePipe(handle: Pointer, data: ByteArray): Boolean {
    val written = IntByReference()
    return kernel32Pipes?.WriteFile(handle, data, data.size, written, null) == true && written.value == data.size
}

private fun readNativePipe(handle: Pointer): String? {
    val buffer = ByteArray(4096)
    val read = IntByReference()
    if (kernel32Pipes?.ReadFile(handle, buffer, buffer.size, read, null) != true || read.value <= 0) return null
    return String(buffer, 0, read.value, Charsets.UTF_8)
}

private fun closeNativePipe(handle: Pointer) {
    runCatching { kernel32Pipes?.CloseHandle(handle) }
}
