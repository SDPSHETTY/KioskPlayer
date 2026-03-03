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
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
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

    private val viewModel: MainViewModel by viewModels()
    private lateinit var imageTimer: ImageTimer

    private var player: ExoPlayer? = null
    private var currentConfig: KioskConfig? = null
    private var permissionRequestInFlight = false

    private var currentItems: List<PlayItem> = emptyList()
    private var currentIndex = 0
    private var lastPlayerError: String? = null


    private val restrictionsChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            if (action == ManagedConfigReceiver.ACTION_CONFIG_CHANGED || action == ControlReceiver.ACTION_REFRESH_NOW) {
                // Force immediate configuration refresh via ViewModel
                viewModel.forceConfigurationRefresh()
            }
            if (action == ControlReceiver.ACTION_HEALTH_DUMP_INTERNAL || action == ControlReceiver.ACTION_HEALTH_DUMP) {
                viewModel.dumpHealthToLog()
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

        // Initialize image timer with lifecycle scope
        imageTimer = ImageTimer(lifecycleScope)

        // Set up ViewModel observers
        setupViewModelObservers()

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

        // Start configuration polling via ViewModel
        viewModel.startConfigurationPolling()
    }

    override fun onStop() {
        // Stop configuration polling
        viewModel.stopConfigurationPolling()

        // Stop image timer
        imageTimer.stopTimer()

        try {
            unregisterReceiver(restrictionsChangedReceiver)
        } catch (_: IllegalArgumentException) {
        }
        super.onStop()
    }

    override fun onDestroy() {
        // Cleanup image timer
        imageTimer.cleanup()
        releasePlayer()
        super.onDestroy()
    }

    /**
     * Sets up observers for ViewModel state changes
     */
    private fun setupViewModelObservers() {
        // Observe configuration changes
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.configUpdateTrigger.collect { _ ->
                    // Configuration has changed, apply it
                    applyConfigAndStartPlayback(force = true)
                }
            }
        }

        // Observe current configuration
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.currentConfig.collect { config ->
                    currentConfig = config
                }
            }
        }
    }

    private fun ensurePlayer() {
        if (player != null) return
        val exoPlayer = ExoPlayer.Builder(this).build().apply {
            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    // Use enhanced error handling
                    val errorInfo = ErrorHandler.handleMediaError(error)
                    lastPlayerError = errorInfo.code

                    ErrorHandler.logError(errorInfo)

                    // Create user-friendly error message
                    val userMessage = ErrorHandler.createUserMessage(errorInfo)

                    val cfg = currentConfig
                    if (cfg != null) {
                        // Record health with structured error
                        recordHealth(cfg, "ERROR", errorInfo.code, currentItems.size)

                        // Show user-friendly error message
                        showStatus(true, userMessage)

                        // Attempt recovery based on configuration
                        if (cfg.fallbackOnStreamError) {
                            ErrorHandler.logInfo(
                                ErrorHandler.ErrorCategory.MEDIA_PLAYBACK,
                                "FALLBACK_ATTEMPT",
                                "Attempting fallback to next media item",
                                mapOf("currentIndex" to currentIndex, "totalItems" to currentItems.size)
                            )
                            playNextFromSequence(allowWrap = false)
                        }
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
            val errorInfo = ErrorHandler.ErrorInfo(
                ErrorHandler.ErrorCategory.PERMISSION,
                "STORAGE_PERMISSION",
                "Storage permission required to read media files"
            )
            ErrorHandler.logWarning(
                errorInfo.category,
                errorInfo.code,
                errorInfo.message
            )
            showStatus(true, ErrorHandler.createUserMessage(errorInfo))
            return
        }

        val config = try {
            KioskConfig.fromRestrictions(this)
        } catch (e: Exception) {
            val errorInfo = ErrorHandler.ErrorInfo(
                ErrorHandler.ErrorCategory.CONFIGURATION,
                "CONFIG_LOAD_FAILED",
                "Failed to load configuration from managed restrictions",
                mapOf("force" to force),
                e
            )
            ErrorHandler.logError(errorInfo)
            showStatus(true, ErrorHandler.createUserMessage(errorInfo))
            return
        }

        // Validate configuration
        val configError = ErrorHandler.validateConfiguration(config)
        if (configError != null) {
            ErrorHandler.logError(configError)
            showStatus(true, ErrorHandler.createUserMessage(configError))
            recordHealth(config, "CONFIG_ERROR", configError.code, 0)
            return
        }

        val previous = currentConfig
        currentConfig = config

        // Log successful configuration load
        ErrorHandler.logInfo(
            ErrorHandler.ErrorCategory.CONFIGURATION,
            "CONFIG_APPLIED",
            "Configuration successfully applied",
            mapOf(
                "mode" to config.playMode,
                "loop" to config.loopMode,
                "path" to config.videoDir,
                "source_pref" to config.sourcePreference,
                "force" to force
            )
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

            val errorInfo = ErrorHandler.ErrorInfo(
                ErrorHandler.ErrorCategory.FILE_SYSTEM,
                "NO_MEDIA",
                "No playable media files found",
                mapOf(
                    "videoDir" to config.videoDir,
                    "streamUrl" to config.streamUrl,
                    "playMode" to config.playMode,
                    "sourcePreference" to config.sourcePreference
                )
            )

            ErrorHandler.logError(errorInfo)

            val message = if (config.showDebugOverlay) {
                buildOverlay(config, "No playable files found.", emptyList())
            } else {
                ErrorHandler.createUserMessage(errorInfo)
            }
            showStatus(true, message)
            recordHealth(config, "NO_MEDIA", "no_media", 0)
            return
        }

        if (!config.skipMissingFiles && hasMissingFiles(config)) {
            stopPlayback()

            val missingFiles = getMissingFiles(config)
            val errorInfo = ErrorHandler.ErrorInfo(
                ErrorHandler.ErrorCategory.FILE_SYSTEM,
                "MISSING_FILES",
                "Required media files are missing",
                mapOf(
                    "missingCount" to missingFiles.size,
                    "missingFiles" to missingFiles.take(5), // Log first 5 missing files
                    "skipMissingFiles" to config.skipMissingFiles
                )
            )

            ErrorHandler.logError(errorInfo)
            showStatus(true, ErrorHandler.createUserMessage(errorInfo))
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
        return getMissingFiles(config).isNotEmpty()
    }

    private fun getMissingFiles(config: KioskConfig): List<String> {
        val baseDir = resolveBaseDirectory(config.videoDir)
        val requested = when {
            config.playMode == "single" && config.singleFile.isNotBlank() -> listOf(config.singleFile)
            config.playlistFiles.isNotEmpty() -> config.playlistFiles
            else -> emptyList()
        }
        if (requested.isEmpty()) return emptyList()

        return requested.filter { entry ->
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
                val errorInfo = ErrorHandler.ErrorInfo(
                    ErrorHandler.ErrorCategory.MEDIA_PLAYBACK,
                    "IMAGE_DECODE_FAILED",
                    "Failed to decode image file",
                    mapOf(
                        "path" to item.path,
                        "index" to currentIndex,
                        "totalItems" to currentItems.size
                    )
                )
                ErrorHandler.logError(errorInfo)

                // Skip to next item
                playNextFromSequence(allowWrap = true)
                return
            }

            imageView.setImageBitmap(bitmap)

            // Start image timer using coroutines
            imageTimer.startTimer(config.imageDurationSec) {
                playNextFromSequence(allowWrap = true)
            }
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
        imageTimer.stopTimer()
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
        viewModel.recordHealth(state, error, mediaCount)
    }

    companion object {
        private const val TAG = "KioskPlayer"

        fun start(context: Context) {
            val intent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(intent)
        }
    }
}
