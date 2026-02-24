package com.esper.kioskplayer

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.io.File
import java.time.DayOfWeek
import java.time.LocalTime

class MainActivity : AppCompatActivity() {

    private data class PlayItem(
        val path: String,
        val type: ItemType,
        val isStream: Boolean = false,
    )

    private enum class ItemType {
        VIDEO,
        IMAGE,
    }

    private lateinit var playerView: PlayerView
    private lateinit var imageView: ImageView
    private lateinit var statusText: TextView

    private val mainHandler = Handler(Looper.getMainLooper())
    private var player: ExoPlayer? = null
    private var currentConfig: KioskConfig? = null
    private var permissionRequestInFlight = false

    private var currentItems: List<PlayItem> = emptyList()
    private var currentIndex = 0
    private var imageAdvanceRunnable: Runnable? = null

    private var lastPlayerError: String? = null

    private val configPollRunnable = object : Runnable {
        override fun run() {
            applyConfigAndStartPlayback(force = false)
            val next = currentConfig?.heartbeatSec ?: 60
            mainHandler.postDelayed(this, next * 1000L)
        }
    }

    private val restrictionsChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            if (action == ManagedConfigReceiver.ACTION_CONFIG_CHANGED || action == ControlReceiver.ACTION_REFRESH_NOW) {
                applyConfigAndStartPlayback(force = true)
            }
            if (action == ControlReceiver.ACTION_HEALTH_DUMP_INTERNAL || action == ControlReceiver.ACTION_HEALTH_DUMP) {
                dumpHealthToLog()
            }
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        permissionRequestInFlight = false
        applyConfigAndStartPlayback(force = true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        playerView = findViewById(R.id.playerView)
        imageView = findViewById(R.id.imageView)
        statusText = findViewById(R.id.statusText)

        ensurePlayer()
        applyConfigAndStartPlayback(force = true)
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(ManagedConfigReceiver.ACTION_CONFIG_CHANGED)
            addAction(ControlReceiver.ACTION_REFRESH_NOW)
            addAction(ControlReceiver.ACTION_HEALTH_DUMP)
            addAction(ControlReceiver.ACTION_HEALTH_DUMP_INTERNAL)
        }
        ContextCompat.registerReceiver(this, restrictionsChangedReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        mainHandler.post(configPollRunnable)
    }

    override fun onStop() {
        mainHandler.removeCallbacks(configPollRunnable)
        stopImageTimer()
        try {
            unregisterReceiver(restrictionsChangedReceiver)
        } catch (_: IllegalArgumentException) {
        }
        super.onStop()
    }

    override fun onDestroy() {
        releasePlayer()
        super.onDestroy()
    }

    private fun ensurePlayer() {
        if (player != null) return
        val exoPlayer = ExoPlayer.Builder(this).build().apply {
            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    lastPlayerError = "${error.errorCodeName} ${error.message ?: ""}".trim()
                    Log.e(TAG, "Playback error: $lastPlayerError")
                    val cfg = currentConfig
                    if (cfg?.fallbackOnStreamError == true) {
                        playNextFromSequence(allowWrap = false)
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    val stateLabel = when (playbackState) {
                        Player.STATE_IDLE -> "IDLE"
                        Player.STATE_BUFFERING -> "BUFFERING"
                        Player.STATE_READY -> "READY"
                        Player.STATE_ENDED -> "ENDED"
                        else -> playbackState.toString()
                    }
                    Log.i(TAG, "Playback state=$stateLabel repeatMode=${player?.repeatMode} playWhenReady=${player?.playWhenReady}")
                    if (playbackState == Player.STATE_ENDED) {
                        playNextFromSequence(allowWrap = true)
                    }
                }
            })
        }
        playerView.player = exoPlayer
        player = exoPlayer
    }

    private fun releasePlayer() {
        playerView.player = null
        player?.release()
        player = null
    }

    private fun applyConfigAndStartPlayback(force: Boolean) {
        ensurePlayer()
        if (!requestMediaReadPermissionIfNeeded()) {
            showStatus(true, "Storage permission required to read media files.")
            return
        }

        val config = KioskConfig.fromRestrictions(this)
        val previous = currentConfig
        currentConfig = config

        Log.i(
            TAG,
            "Applying config mode=${config.playMode} loop=${config.loopMode} path=${config.videoDir} source_pref=${config.sourcePreference}"
        )

        applyWindowMode(config)
        applyOrientation(config)
        playerView.useController = !config.hideControls

        if (!isWithinSchedule(config)) {
            stopPlayback()
            showStatus(true, "Playback is outside scheduled hours.")
            recordHealth(config, "SCHEDULED_OFF", "outside_schedule", 0)
            return
        }

        val items = buildPlayItems(config)
        if (items.isEmpty()) {
            stopPlayback()
            val message = if (config.showDebugOverlay) {
                buildOverlay(config, "No playable files found.", emptyList())
            } else {
                "No media found. Check path, stream URL, and file names in managed config."
            }
            showStatus(true, message)
            recordHealth(config, "NO_MEDIA", "no_media", 0)
            return
        }

        if (!config.skipMissingFiles && hasMissingFiles(config)) {
            stopPlayback()
            showStatus(true, "Some media files are missing. Upload files or update config.")
            recordHealth(config, "MISSING_MEDIA", "missing_files", 0)
            return
        }

        val shouldReload = force || previous != config || currentItems.isEmpty()
        if (shouldReload) {
            currentItems = items
            currentIndex = 0
            playCurrentItem(config)
        }

        val overlay = buildOverlay(config, "Playing ${currentItems.size} item(s)", currentItems.map { File(it.path) })
        showStatus(config.showDebugOverlay, overlay)
        recordHealth(config, "PLAYING", null, currentItems.size)
    }

    private fun buildPlayItems(config: KioskConfig): List<PlayItem> {
        val localItems = buildLocalItems(config)
        val streamItem = config.streamUrl.takeIf { it.isNotBlank() }?.let { PlayItem(it, ItemType.VIDEO, isStream = true) }

        return when (config.sourcePreference) {
            "stream_only" -> listOfNotNull(streamItem)
            "stream_first" -> listOfNotNull(streamItem) + localItems
            "local_only" -> localItems
            else -> localItems + listOfNotNull(streamItem)
        }
    }

    private fun buildLocalItems(config: KioskConfig): List<PlayItem> {
        val baseDir = resolveBaseDirectory(config.videoDir)
        val requested = when {
            config.playMode == "single" && config.singleFile.isNotBlank() -> listOf(config.singleFile)
            config.playlistFiles.isNotEmpty() -> config.playlistFiles
            else -> listFilesFromDirectory(baseDir).map { it.absolutePath }
        }

        return requested.map { resolveFile(baseDir, it) }
            .filter { it.exists() && it.isFile && isSupportedMediaFile(it) }
            .map { file ->
                val type = if (isImageFile(file)) ItemType.IMAGE else ItemType.VIDEO
                PlayItem(file.absolutePath, type)
            }
    }

    private fun hasMissingFiles(config: KioskConfig): Boolean {
        val baseDir = resolveBaseDirectory(config.videoDir)
        val requested = when {
            config.playMode == "single" && config.singleFile.isNotBlank() -> listOf(config.singleFile)
            config.playlistFiles.isNotEmpty() -> config.playlistFiles
            else -> emptyList()
        }
        if (requested.isEmpty()) return false
        return requested.any { entry ->
            val f = resolveFile(baseDir, entry)
            !f.exists() || !f.isFile || !isSupportedMediaFile(f)
        }
    }

    private fun playCurrentItem(config: KioskConfig) {
        if (currentItems.isEmpty()) return
        val item = currentItems[currentIndex.coerceIn(0, currentItems.lastIndex)]
        stopImageTimer()

        if (item.type == ItemType.IMAGE) {
            player?.stop()
            playerView.visibility = View.GONE
            imageView.visibility = View.VISIBLE

            val bitmap = BitmapFactory.decodeFile(item.path)
            if (bitmap == null) {
                playNextFromSequence(allowWrap = true)
                return
            }

            imageView.setImageBitmap(bitmap)
            val delayMs = (config.imageDurationSec.coerceAtLeast(1) * 1000L)
            imageAdvanceRunnable = Runnable { playNextFromSequence(allowWrap = true) }
            mainHandler.postDelayed(imageAdvanceRunnable!!, delayMs)
            return
        }

        imageView.visibility = View.GONE
        playerView.visibility = View.VISIBLE

        val exoPlayer = player ?: return
        exoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(item.path)), true)
        exoPlayer.repeatMode = Player.REPEAT_MODE_OFF
        exoPlayer.setPauseAtEndOfMediaItems(config.loopMode == "once")
        exoPlayer.volume = if (config.mute) 0f else config.volumePercent / 100f
        exoPlayer.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    private fun playNextFromSequence(allowWrap: Boolean) {
        val config = currentConfig ?: return
        if (currentItems.isEmpty()) return

        when (config.loopMode) {
            "loop_one" -> {
                playCurrentItem(config)
                return
            }

            "loop_all" -> {
                currentIndex++
                if (currentIndex > currentItems.lastIndex) currentIndex = 0
            }

            else -> {
                currentIndex++
                if (currentIndex > currentItems.lastIndex) {
                    if (allowWrap) {
                        stopPlayback()
                        showStatus(true, "Playlist completed.")
                        recordHealth(config, "ENDED", null, currentItems.size)
                    }
                    return
                }
            }
        }
        playCurrentItem(config)
    }

    private fun stopPlayback() {
        stopImageTimer()
        imageView.visibility = View.GONE
        playerView.visibility = View.VISIBLE
        player?.stop()
    }

    private fun stopImageTimer() {
        imageAdvanceRunnable?.let { mainHandler.removeCallbacks(it) }
        imageAdvanceRunnable = null
    }

    private fun isWithinSchedule(config: KioskConfig): Boolean {
        if (!config.scheduleEnabled) return true
        val now = java.time.LocalDateTime.now()
        val day = when (now.dayOfWeek) {
            DayOfWeek.MONDAY -> 1
            DayOfWeek.TUESDAY -> 2
            DayOfWeek.WEDNESDAY -> 3
            DayOfWeek.THURSDAY -> 4
            DayOfWeek.FRIDAY -> 5
            DayOfWeek.SATURDAY -> 6
            DayOfWeek.SUNDAY -> 7
        }
        if (!config.scheduleDays.contains(day)) return false

        val start = parseTime(config.scheduleStart) ?: return true
        val end = parseTime(config.scheduleEnd) ?: return true
        val current = now.toLocalTime()

        return if (end.isAfter(start) || end == start) {
            current >= start && current <= end
        } else {
            current >= start || current <= end
        }
    }

    private fun parseTime(value: String): LocalTime? {
        return try {
            val parts = value.trim().split(':')
            if (parts.size != 2) return null
            LocalTime.of(parts[0].toInt(), parts[1].toInt())
        } catch (_: Exception) {
            null
        }
    }

    private fun applyWindowMode(config: KioskConfig) {
        WindowCompat.setDecorFitsSystemWindows(window, !config.fullscreen)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (config.fullscreen) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun applyOrientation(config: KioskConfig) {
        requestedOrientation = when (config.orientation) {
            "portrait" -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            "auto" -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            else -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
    }

    private fun resolveBaseDirectory(path: String): File? {
        if (path.isBlank()) return null
        val direct = File(path)
        if (direct.isAbsolute) return direct

        val candidates = listOfNotNull(
            getExternalFilesDir(null)?.let { File(it, path) },
            File(filesDir, path),
            File(Environment.getExternalStorageDirectory(), path),
        )
        return candidates.firstOrNull { it.exists() } ?: candidates.firstOrNull()
    }

    private fun resolveFile(baseDir: File?, entry: String): File {
        val cleaned = entry.trim()
        if (cleaned.startsWith("/")) return File(cleaned)
        return if (baseDir != null) File(baseDir, cleaned) else File(cleaned)
    }

    private fun listFilesFromDirectory(baseDir: File?): List<File> {
        if (baseDir == null || !baseDir.exists() || !baseDir.isDirectory) return emptyList()
        return baseDir.listFiles()?.filter { it.isFile && isSupportedMediaFile(it) }?.sortedBy { it.name.lowercase() } ?: emptyList()
    }

    private fun isSupportedMediaFile(file: File): Boolean = isVideoFile(file) || isImageFile(file)

    private fun isVideoFile(file: File): Boolean {
        val name = file.name.lowercase()
        return name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".webm") || name.endsWith(".mov") || name.endsWith(".m4v")
    }

    private fun isImageFile(file: File): Boolean {
        val name = file.name.lowercase()
        return name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".gif") || name.endsWith(".webp")
    }

    private fun buildOverlay(config: KioskConfig, headline: String, files: List<File>): String {
        val preview = files.take(5).joinToString("\n") { "- ${it.absolutePath}" }
        return """
            $headline
            mode=${config.playMode}
            loop=${config.loopMode}
            path=${config.videoDir}
            source_preference=${config.sourcePreference}
            stream_url=${config.streamUrl.takeIf { it.isNotBlank() } ?: "<none>"}
            schedule_enabled=${config.scheduleEnabled}
            count=${files.size}
            files:
            $preview
        """.trimIndent()
    }

    private fun showStatus(show: Boolean, text: String) {
        statusText.visibility = if (show) View.VISIBLE else View.GONE
        statusText.text = text
    }

    private fun requestMediaReadPermissionIfNeeded(): Boolean {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33) {
            if (!hasPermission(Manifest.permission.READ_MEDIA_VIDEO)) permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
            if (!hasPermission(Manifest.permission.READ_MEDIA_IMAGES)) permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            if (!hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE)) permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (permissions.isNotEmpty()) {
            if (!permissionRequestInFlight) {
                permissionRequestInFlight = true
                permissionLauncher.launch(permissions.toTypedArray())
            }
            return false
        }
        return true
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun recordHealth(config: KioskConfig, state: String, error: String?, mediaCount: Int) {
        val prefs = getSharedPreferences(HEALTH_PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putLong("last_update_ms", System.currentTimeMillis())
            .putString("state", state)
            .putString("error", error)
            .putInt("media_count", mediaCount)
            .putString("path", config.videoDir)
            .putString("mode", config.playMode)
            .putString("loop", config.loopMode)
            .apply()
    }

    private fun dumpHealthToLog() {
        val prefs = getSharedPreferences(HEALTH_PREFS, Context.MODE_PRIVATE)
        Log.i(
            TAG,
            "health state=${prefs.getString("state", "unknown")} error=${prefs.getString("error", null)} media_count=${prefs.getInt("media_count", 0)} updated=${prefs.getLong("last_update_ms", 0)}"
        )
    }

    companion object {
        private const val TAG = "KioskPlayer"
        private const val HEALTH_PREFS = "kioskplayer_health"

        fun start(context: Context) {
            val intent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(intent)
        }
    }
}
