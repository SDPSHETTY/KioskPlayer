package com.esper.kioskplayer

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.os.Environment
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.io.File

class MainActivity : AppCompatActivity() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val configPollRunnable = object : Runnable {
        override fun run() {
            applyConfigAndStartPlayback(force = false)
            mainHandler.postDelayed(this, 10000)
        }
    }

    private lateinit var playerView: PlayerView
    private lateinit var statusText: TextView

    private var player: ExoPlayer? = null
    private var currentConfig: KioskConfig? = null
    private var permissionRequestInFlight = false

    private val restrictionsChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ManagedConfigReceiver.ACTION_CONFIG_CHANGED) {
                applyConfigAndStartPlayback(force = true)
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
        statusText = findViewById(R.id.statusText)

        ensurePlayer()
        applyConfigAndStartPlayback(force = true)
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(ManagedConfigReceiver.ACTION_CONFIG_CHANGED)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(restrictionsChangedReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(restrictionsChangedReceiver, filter)
        }
        mainHandler.post(configPollRunnable)
    }

    override fun onStop() {
        mainHandler.removeCallbacks(configPollRunnable)
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
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    statusText.visibility = View.VISIBLE
                    statusText.text = "Playback error: ${error.errorCodeName} ${error.message ?: ""}".trim()
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
            showStatus(true, "Storage permission required to read videos")
            return
        }

        val config = KioskConfig.fromRestrictions(this)
        val previous = currentConfig
        currentConfig = config

        Log.i(
            TAG,
            "Applying config play_mode=${config.playMode} loop_mode=${config.loopMode} fullscreen=${config.fullscreen} " +
                "hide_controls=${config.hideControls} orientation=${config.orientation} mute=${config.mute} " +
                "volume_percent=${config.volumePercent} autostart_on_boot=${config.autostartOnBoot} " +
                "skip_missing_files=${config.skipMissingFiles}"
        )

        applyWindowMode(config)
        applyOrientation(config)
        playerView.useController = !config.hideControls

        val files = buildTargetFileList(config)
        val playableFiles = files.filter { it.exists() && it.isFile && isVideoFile(it) }

        if (playableFiles.isEmpty()) {
            player?.stop()
            Log.w(TAG, "No playable files found for video_dir=${config.videoDir}")
            val message = if (config.showDebugOverlay) {
                buildOverlay(config, "No playable files found.", files)
            } else {
                "No videos found. Please check the path and file names in managed config."
            }
            showStatus(true, message)
            return
        }

        if (!config.skipMissingFiles && playableFiles.size != files.size) {
            player?.stop()
            Log.w(TAG, "Missing files detected and skip_missing_files=false")
            val message = if (config.showDebugOverlay) {
                buildOverlay(config, "Some configured files are missing and skip_missing_files=false.", files)
            } else {
                "Some videos are missing. Update managed config or upload missing files."
            }
            showStatus(true, message)
            return
        }

        val mediaItems = playableFiles.map { MediaItem.fromUri(Uri.fromFile(it)) }
        val shouldReloadMedia = force || previous != config
        val exoPlayer = player ?: return

        if (shouldReloadMedia) {
            exoPlayer.setMediaItems(mediaItems, true)
            exoPlayer.prepare()
        }

        exoPlayer.repeatMode = when (config.loopMode) {
            "loop_one" -> Player.REPEAT_MODE_ONE
            "loop_all" -> Player.REPEAT_MODE_ALL
            else -> Player.REPEAT_MODE_OFF
        }
        exoPlayer.setPauseAtEndOfMediaItems(config.loopMode == "once")
        exoPlayer.volume = if (config.mute) 0f else config.volumePercent / 100f
        exoPlayer.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
        exoPlayer.playWhenReady = true

        showStatus(
            config.showDebugOverlay,
            buildOverlay(config, "Playing ${playableFiles.size} file(s)", playableFiles)
        )
    }

    private fun applyWindowMode(config: KioskConfig) {
        WindowCompat.setDecorFitsSystemWindows(window, !config.fullscreen)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (config.fullscreen) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
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

    private fun buildTargetFileList(config: KioskConfig): List<File> {
        val baseDir = resolveBaseDirectory(config.videoDir)

        if (config.playMode == "single") {
            if (config.singleFile.isBlank()) return emptyList()
            return listOf(resolveFile(baseDir, config.singleFile))
        }

        if (config.playlistFiles.isNotEmpty()) {
            return config.playlistFiles.map { resolveFile(baseDir, it) }
        }

        return listFilesFromDirectory(baseDir)
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
        if (baseDir != null) return File(baseDir, cleaned)
        return File(cleaned)
    }

    private fun listFilesFromDirectory(baseDir: File?): List<File> {
        if (baseDir == null || !baseDir.exists() || !baseDir.isDirectory) return emptyList()
        return baseDir.listFiles()
            ?.filter { it.isFile && isVideoFile(it) }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()
    }

    private fun isVideoFile(file: File): Boolean {
        val name = file.name.lowercase()
        return name.endsWith(".mp4") ||
            name.endsWith(".mkv") ||
            name.endsWith(".webm") ||
            name.endsWith(".mov") ||
            name.endsWith(".m4v")
    }

    private fun buildOverlay(config: KioskConfig, headline: String, files: List<File>): String {
        val filePreview = files.take(5).joinToString("\n") { "- ${it.absolutePath}" }
        return """
            $headline
            play_mode=${config.playMode}
            loop_mode=${config.loopMode}
            video_dir=${config.videoDir}
            count=${files.size}
            files:
            $filePreview
        """.trimIndent()
    }

    private fun showStatus(show: Boolean, text: String) {
        statusText.visibility = if (show) View.VISIBLE else View.GONE
        statusText.text = text
    }

    private fun requestMediaReadPermissionIfNeeded(): Boolean {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= 33) {
            if (!hasPermission(Manifest.permission.READ_MEDIA_VIDEO)) {
                permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
            }
        } else {
            if (!hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
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

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
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
