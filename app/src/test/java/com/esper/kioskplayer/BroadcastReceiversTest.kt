package com.esper.kioskplayer

import android.content.Context
import android.content.Intent
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class BroadcastReceiversTest {

    @Mock
    private lateinit var mockContext: Context

    private lateinit var managedConfigReceiver: ManagedConfigReceiver
    private lateinit var debugConfigReceiver: DebugConfigReceiver
    private lateinit var controlReceiver: ControlReceiver

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        managedConfigReceiver = ManagedConfigReceiver()
        debugConfigReceiver = DebugConfigReceiver()
        controlReceiver = ControlReceiver()
    }

    @Test
    fun `ManagedConfigReceiver handles BOOT_COMPLETED intent safely`() {
        // Given
        val intent = Intent().apply {
            action = "android.intent.action.BOOT_COMPLETED"
        }

        // When & Then - Should not crash
        try {
            managedConfigReceiver.onReceive(mockContext, intent)
            // If we reach here, the receiver handled the intent without crashing
        } catch (e: Exception) {
            throw AssertionError("ManagedConfigReceiver should handle BOOT_COMPLETED safely", e)
        }
    }

    @Test
    fun `ManagedConfigReceiver handles APPLICATION_RESTRICTIONS_CHANGED intent safely`() {
        // Given
        val intent = Intent().apply {
            action = "android.intent.action.APPLICATION_RESTRICTIONS_CHANGED"
        }

        // When & Then - Should not crash
        try {
            managedConfigReceiver.onReceive(mockContext, intent)
        } catch (e: Exception) {
            throw AssertionError("ManagedConfigReceiver should handle APPLICATION_RESTRICTIONS_CHANGED safely", e)
        }
    }

    @Test
    fun `ManagedConfigReceiver handles LOCKED_BOOT_COMPLETED intent safely`() {
        // Given
        val intent = Intent().apply {
            action = "android.intent.action.LOCKED_BOOT_COMPLETED"
        }

        // When & Then - Should not crash
        try {
            managedConfigReceiver.onReceive(mockContext, intent)
        } catch (e: Exception) {
            throw AssertionError("ManagedConfigReceiver should handle LOCKED_BOOT_COMPLETED safely", e)
        }
    }

    @Test
    fun `ManagedConfigReceiver handles MY_PACKAGE_REPLACED intent safely`() {
        // Given
        val intent = Intent().apply {
            action = "android.intent.action.MY_PACKAGE_REPLACED"
        }

        // When & Then - Should not crash
        try {
            managedConfigReceiver.onReceive(mockContext, intent)
        } catch (e: Exception) {
            throw AssertionError("ManagedConfigReceiver should handle MY_PACKAGE_REPLACED safely", e)
        }
    }

    @Test
    fun `DebugConfigReceiver handles DEBUG_APPLY_CONFIG intent safely`() {
        // Given
        val intent = Intent().apply {
            action = "com.esper.kioskplayer.DEBUG_APPLY_CONFIG"
        }

        // When & Then - Should not crash (but requires permission now)
        try {
            debugConfigReceiver.onReceive(mockContext, intent)
        } catch (e: Exception) {
            throw AssertionError("DebugConfigReceiver should handle DEBUG_APPLY_CONFIG safely", e)
        }
    }

    @Test
    fun `DebugConfigReceiver handles DEBUG_CLEAR_CONFIG intent safely`() {
        // Given
        val intent = Intent().apply {
            action = "com.esper.kioskplayer.DEBUG_CLEAR_CONFIG"
        }

        // When & Then - Should not crash (but requires permission now)
        try {
            debugConfigReceiver.onReceive(mockContext, intent)
        } catch (e: Exception) {
            throw AssertionError("DebugConfigReceiver should handle DEBUG_CLEAR_CONFIG safely", e)
        }
    }

    @Test
    fun `ControlReceiver handles REFRESH_NOW intent safely`() {
        // Given
        val intent = Intent().apply {
            action = "com.esper.kioskplayer.REFRESH_NOW"
        }

        // When & Then - Should not crash (but requires permission now)
        try {
            controlReceiver.onReceive(mockContext, intent)
        } catch (e: Exception) {
            throw AssertionError("ControlReceiver should handle REFRESH_NOW safely", e)
        }
    }

    @Test
    fun `ControlReceiver handles HEALTH_DUMP intent safely`() {
        // Given
        val intent = Intent().apply {
            action = "com.esper.kioskplayer.HEALTH_DUMP"
        }

        // When & Then - Should not crash (but requires permission now)
        try {
            controlReceiver.onReceive(mockContext, intent)
        } catch (e: Exception) {
            throw AssertionError("ControlReceiver should handle HEALTH_DUMP safely", e)
        }
    }

    @Test
    fun `ManagedConfigReceiver ignores unknown intents safely`() {
        // Given
        val intent = Intent().apply {
            action = "com.unknown.action"
        }

        // When & Then - Should not crash when receiving unknown actions
        try {
            managedConfigReceiver.onReceive(mockContext, intent)
        } catch (e: Exception) {
            throw AssertionError("ManagedConfigReceiver should ignore unknown intents safely", e)
        }
    }

    @Test
    fun `DebugConfigReceiver ignores unknown intents safely`() {
        // Given
        val intent = Intent().apply {
            action = "com.unknown.action"
        }

        // When & Then - Should not crash when receiving unknown actions
        try {
            debugConfigReceiver.onReceive(mockContext, intent)
        } catch (e: Exception) {
            throw AssertionError("DebugConfigReceiver should ignore unknown intents safely", e)
        }
    }

    @Test
    fun `ControlReceiver ignores unknown intents safely`() {
        // Given
        val intent = Intent().apply {
            action = "com.unknown.action"
        }

        // When & Then - Should not crash when receiving unknown actions
        try {
            controlReceiver.onReceive(mockContext, intent)
        } catch (e: Exception) {
            throw AssertionError("ControlReceiver should ignore unknown intents safely", e)
        }
    }
}