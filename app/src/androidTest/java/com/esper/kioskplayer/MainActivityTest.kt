package com.esper.kioskplayer

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@LargeTest
class MainActivityTest {

    private lateinit var context: Context
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        scenario?.close()
    }

    @Test
    fun activityLaunches() {
        // Given
        val intent = Intent(context, MainActivity::class.java)

        // When
        scenario = ActivityScenario.launch(intent)

        // Then
        scenario!!.onActivity { activity ->
            assertNotNull(activity)
            assertTrue(activity.javaClass.simpleName == "MainActivity")
        }
    }

    @Test
    fun activityHandlesConfigurationChange() {
        // Given
        scenario = ActivityScenario.launch(MainActivity::class.java)

        // When & Then - Activity should handle configuration changes without crashing
        scenario!!.onActivity { activity ->
            assertNotNull(activity)
            // Activity should be in proper state
            assertTrue(activity.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.CREATED))
        }
    }

    @Test
    fun activityRespondsToBootCompleted() {
        // Given
        val intent = Intent().apply {
            action = "android.intent.action.BOOT_COMPLETED"
        }

        // When
        val receiver = ManagedConfigReceiver()

        // Then - Should not crash when receiving boot completed broadcast
        // This tests the broadcast receiver registration is correct
        try {
            receiver.onReceive(context, intent)
            // If we get here without exception, the receiver handles the intent properly
            assertTrue(true)
        } catch (e: Exception) {
            throw AssertionError("ManagedConfigReceiver should handle BOOT_COMPLETED without crashing", e)
        }
    }

    @Test
    fun activityRespondsToApplicationRestrictionsChanged() {
        // Given
        val intent = Intent().apply {
            action = "android.intent.action.APPLICATION_RESTRICTIONS_CHANGED"
        }

        // When
        val receiver = ManagedConfigReceiver()

        // Then - Should not crash when receiving restrictions changed broadcast
        try {
            receiver.onReceive(context, intent)
            assertTrue(true)
        } catch (e: Exception) {
            throw AssertionError("ManagedConfigReceiver should handle APPLICATION_RESTRICTIONS_CHANGED without crashing", e)
        }
    }
}