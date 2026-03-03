package com.esper.kioskplayer

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.RestrictionsManager
import android.os.Bundle
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class KioskConfigTest {

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockRestrictionsManager: RestrictionsManager

    @Mock
    private lateinit var mockSharedPreferences: SharedPreferences

    @Mock
    private lateinit var mockEditor: SharedPreferences.Editor

    @Mock
    private lateinit var mockApplicationInfo: ApplicationInfo

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)

        // Setup default mocks
        whenever(mockContext.getSystemService(Context.RESTRICTIONS_SERVICE)).thenReturn(mockRestrictionsManager)
        whenever(mockContext.getSharedPreferences(any(), any())).thenReturn(mockSharedPreferences)
        whenever(mockContext.applicationInfo).thenReturn(mockApplicationInfo)
        whenever(mockSharedPreferences.edit()).thenReturn(mockEditor)
        whenever(mockEditor.putBoolean(any(), any())).thenReturn(mockEditor)
        whenever(mockEditor.putString(any(), any())).thenReturn(mockEditor)
        whenever(mockEditor.putInt(any(), any())).thenReturn(mockEditor)
        whenever(mockEditor.putLong(any(), any())).thenReturn(mockEditor)
        whenever(mockEditor.putFloat(any(), any())).thenReturn(mockEditor)
        whenever(mockEditor.remove(any())).thenReturn(mockEditor)
        whenever(mockEditor.clear()).thenReturn(mockEditor)

        // Default: not debuggable, no debug overrides
        mockApplicationInfo.flags = 0
        whenever(mockSharedPreferences.getBoolean("debug_override_enabled", false)).thenReturn(false)
    }

    @Test
    fun `fromRestrictions creates config with default values when no restrictions`() {
        // Given
        whenever(mockRestrictionsManager.applicationRestrictions).thenReturn(Bundle.EMPTY)

        // When
        val config = KioskConfig.fromRestrictions(mockContext)

        // Then
        assertEquals("Movies", config.videoDir)
        assertEquals("playlist", config.playMode)
        assertEquals("", config.singleFile)
        assertTrue(config.playlistFiles.isEmpty())
        assertEquals("loop_all", config.loopMode)
        assertEquals(10, config.imageDurationSec)
        assertEquals("", config.streamUrl)
        assertEquals("local_first", config.sourcePreference)
        assertTrue(config.fallbackOnStreamError)
        assertFalse(config.scheduleEnabled)
        assertEquals("00:00", config.scheduleStart)
        assertEquals("23:59", config.scheduleEnd)
        assertEquals(setOf(1,2,3,4,5,6,7), config.scheduleDays)
        assertEquals(60, config.heartbeatSec)
        assertTrue(config.fullscreen)
        assertTrue(config.hideControls)
        assertEquals("landscape", config.orientation)
        assertFalse(config.mute)
        assertEquals(100, config.volumePercent)
        assertTrue(config.autostartOnBoot)
        assertTrue(config.skipMissingFiles)
        assertFalse(config.showDebugOverlay)
    }

    @Test
    fun `fromRestrictions applies managed configuration restrictions`() {
        // Given
        val bundle = Bundle().apply {
            putString("video_dir", "TestVideos")
            putString("play_mode", "single")
            putString("files", "video1.mp4,video2.mp4")
            putString("loop_mode", "once")
            putInt("image_duration_sec", 5)
            putString("stream_url", "https://example.com/stream.m3u8")
            putBoolean("schedule_enabled", true)
            putString("schedule_start", "09:00")
            putString("schedule_end", "17:00")
            putString("schedule_days", "mon,wed,fri")
            putBoolean("mute", true)
            putInt("volume_percent", 75)
        }
        whenever(mockRestrictionsManager.applicationRestrictions).thenReturn(bundle)

        // When
        val config = KioskConfig.fromRestrictions(mockContext)

        // Then
        assertEquals("TestVideos", config.videoDir)
        assertEquals("single", config.playMode)
        assertEquals("video1.mp4", config.singleFile) // First from playlist in single mode
        assertEquals(listOf("video1.mp4", "video2.mp4"), config.playlistFiles)
        assertEquals("once", config.loopMode)
        assertEquals(5, config.imageDurationSec)
        assertEquals("https://example.com/stream.m3u8", config.streamUrl)
        assertTrue(config.scheduleEnabled)
        assertEquals("09:00", config.scheduleStart)
        assertEquals("17:00", config.scheduleEnd)
        assertEquals(setOf(1, 3, 5), config.scheduleDays) // mon=1, wed=3, fri=5
        assertTrue(config.mute)
        assertEquals(75, config.volumePercent)
    }

    @Test
    fun `parseScheduleDays handles various input formats correctly`() {
        // Given
        val bundle = Bundle()
        whenever(mockRestrictionsManager.applicationRestrictions).thenReturn(bundle)

        // Test day name parsing
        bundle.putString("schedule_days", "mon,tue,wed")
        var config = KioskConfig.fromRestrictions(mockContext)
        assertEquals(setOf(1, 2, 3), config.scheduleDays)

        // Test numeric parsing
        bundle.putString("schedule_days", "1,3,5,7")
        config = KioskConfig.fromRestrictions(mockContext)
        assertEquals(setOf(1, 3, 5, 7), config.scheduleDays)

        // Test mixed format
        bundle.putString("schedule_days", "mon,2,fri")
        config = KioskConfig.fromRestrictions(mockContext)
        assertEquals(setOf(1, 2, 5), config.scheduleDays)

        // Test semicolon separator
        bundle.putString("schedule_days", "tue;thu;sat")
        config = KioskConfig.fromRestrictions(mockContext)
        assertEquals(setOf(2, 4, 6), config.scheduleDays)

        // Test empty/invalid returns all days
        bundle.putString("schedule_days", "")
        config = KioskConfig.fromRestrictions(mockContext)
        assertEquals(setOf(1, 2, 3, 4, 5, 6, 7), config.scheduleDays)

        bundle.putString("schedule_days", "invalid,xyz,9")
        config = KioskConfig.fromRestrictions(mockContext)
        assertEquals(setOf(1, 2, 3, 4, 5, 6, 7), config.scheduleDays)
    }

    @Test
    fun `loop mode normalization works correctly`() {
        // Given
        val bundle = Bundle()
        whenever(mockRestrictionsManager.applicationRestrictions).thenReturn(bundle)

        // Test various loop mode inputs
        val testCases = mapOf(
            "one" to "loop_one",
            "loop_one" to "loop_one",
            "all" to "loop_all",
            "loop_all" to "loop_all",
            "off" to "once",
            "once" to "once",
            "invalid" to "loop_all",
            "" to "loop_all"
        )

        testCases.forEach { (input, expected) ->
            bundle.putString("loop_mode", input)
            val config = KioskConfig.fromRestrictions(mockContext)
            assertEquals(expected, config.loopMode, "Failed for input: $input")
        }
    }

    @Test
    fun `value coercion works correctly for numeric fields`() {
        // Given
        val bundle = Bundle().apply {
            putInt("image_duration_sec", 5000) // Above max (3600)
            putInt("heartbeat_sec", 5) // Below min (15)
            putInt("volume_percent", 150) // Above max (100)
        }
        whenever(mockRestrictionsManager.applicationRestrictions).thenReturn(bundle)

        // When
        val config = KioskConfig.fromRestrictions(mockContext)

        // Then
        assertEquals(3600, config.imageDurationSec) // Coerced to max
        assertEquals(15, config.heartbeatSec) // Coerced to min
        assertEquals(100, config.volumePercent) // Coerced to max
    }

    @Test
    fun `controls string parsing works correctly`() {
        // Given
        val bundle = Bundle()
        whenever(mockRestrictionsManager.applicationRestrictions).thenReturn(bundle)

        // Test "show" controls
        bundle.putString("controls", "show")
        var config = KioskConfig.fromRestrictions(mockContext)
        assertFalse(config.hideControls)

        // Test "hide" controls
        bundle.putString("controls", "hide")
        config = KioskConfig.fromRestrictions(mockContext)
        assertTrue(config.hideControls)

        // Test fallback to hide_controls boolean
        bundle.remove("controls")
        bundle.putBoolean("hide_controls", false)
        config = KioskConfig.fromRestrictions(mockContext)
        assertFalse(config.hideControls)
    }

    @Test
    fun `debug overrides take precedence when enabled in debuggable build`() {
        // Given - debuggable build with debug overrides enabled
        mockApplicationInfo.flags = ApplicationInfo.FLAG_DEBUGGABLE
        whenever(mockSharedPreferences.getBoolean("debug_override_enabled", false)).thenReturn(true)
        whenever(mockSharedPreferences.getString("video_dir", null)).thenReturn("DebugVideos")
        whenever(mockSharedPreferences.contains("mute")).thenReturn(true)
        whenever(mockSharedPreferences.getBoolean("mute", false)).thenReturn(true)

        val bundle = Bundle().apply {
            putString("video_dir", "ProdVideos")
            putBoolean("mute", false)
        }
        whenever(mockRestrictionsManager.applicationRestrictions).thenReturn(bundle)

        // When
        val config = KioskConfig.fromRestrictions(mockContext)

        // Then - debug values should override managed config
        assertEquals("DebugVideos", config.videoDir)
        assertTrue(config.mute)
    }

    @Test
    fun `debug overrides ignored in non-debuggable build`() {
        // Given - non-debuggable build
        mockApplicationInfo.flags = 0
        whenever(mockSharedPreferences.getBoolean("debug_override_enabled", false)).thenReturn(true)
        whenever(mockSharedPreferences.getString("video_dir", null)).thenReturn("DebugVideos")

        val bundle = Bundle().apply {
            putString("video_dir", "ProdVideos")
        }
        whenever(mockRestrictionsManager.applicationRestrictions).thenReturn(bundle)

        // When
        val config = KioskConfig.fromRestrictions(mockContext)

        // Then - managed config should be used
        assertEquals("ProdVideos", config.videoDir)
    }

    @Test
    fun `updateDebugOverrides clears all preferences when clearAll is true`() {
        // When
        KioskConfig.updateDebugOverrides(mockContext, emptyMap(), clearAll = true)

        // Then
        verify(mockEditor).clear()
        verify(mockEditor).putBoolean("debug_override_enabled", false)
        verify(mockEditor).apply()
    }

    @Test
    fun `updateDebugOverrides saves various data types correctly`() {
        // Given
        val values = mapOf<String, Any?>(
            "string_key" to "test_value",
            "boolean_key" to true,
            "int_key" to 42,
            "long_key" to 123L,
            "float_key" to 3.14f,
            "null_key" to null
        )

        // When
        KioskConfig.updateDebugOverrides(mockContext, values, clearAll = false)

        // Then
        verify(mockEditor).putBoolean("debug_override_enabled", true)
        verify(mockEditor).putString("string_key", "test_value")
        verify(mockEditor).putBoolean("boolean_key", true)
        verify(mockEditor).putInt("int_key", 42)
        verify(mockEditor).putLong("long_key", 123L)
        verify(mockEditor).putFloat("float_key", 3.14f)
        verify(mockEditor).remove("null_key")
        verify(mockEditor).apply()
    }

    @Test
    fun `playlist parsing handles multiple separators and whitespace`() {
        // Given
        val bundle = Bundle().apply {
            putString("files", "  video1.mp4 , video2.mp4\nvideo3.mp4;  video4.mp4  ")
        }
        whenever(mockRestrictionsManager.applicationRestrictions).thenReturn(bundle)

        // When
        val config = KioskConfig.fromRestrictions(mockContext)

        // Then
        assertEquals(
            listOf("video1.mp4", "video2.mp4", "video3.mp4", "video4.mp4"),
            config.playlistFiles
        )
    }

    @Test
    fun `single mode uses first playlist file when available`() {
        // Given
        val bundle = Bundle().apply {
            putString("play_mode", "single")
            putString("files", "first.mp4,second.mp4,third.mp4")
            putString("single_file", "legacy.mp4")
        }
        whenever(mockRestrictionsManager.applicationRestrictions).thenReturn(bundle)

        // When
        val config = KioskConfig.fromRestrictions(mockContext)

        // Then
        assertEquals("single", config.playMode)
        assertEquals("first.mp4", config.singleFile) // Should use first from playlist, not legacy
    }

    @Test
    fun `single mode falls back to legacy single_file when no playlist`() {
        // Given
        val bundle = Bundle().apply {
            putString("play_mode", "single")
            putString("single_file", "legacy.mp4")
        }
        whenever(mockRestrictionsManager.applicationRestrictions).thenReturn(bundle)

        // When
        val config = KioskConfig.fromRestrictions(mockContext)

        // Then
        assertEquals("single", config.playMode)
        assertEquals("legacy.mp4", config.singleFile)
    }
}