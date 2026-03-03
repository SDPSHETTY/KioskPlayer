package com.esper.kioskplayer

import android.util.Log
import kotlinx.coroutines.*

/**
 * Coroutine-based timer for image slideshow functionality
 * Replaces Handler-based timing with modern coroutines
 */
class ImageTimer(private val scope: CoroutineScope) {

    private var currentTimerJob: Job? = null

    companion object {
        private const val TAG = "ImageTimer"
        private const val MIN_DURATION_SEC = 1
        private const val MAX_DURATION_SEC = 3600
    }

    /**
     * Starts a timer that executes the callback after the specified duration
     * @param durationSec Duration in seconds (will be coerced to valid range)
     * @param callback Function to execute when timer expires
     */
    fun startTimer(durationSec: Int, callback: () -> Unit) {
        // Cancel any existing timer
        stopTimer()

        // Validate and coerce duration
        val validDuration = durationSec.coerceIn(MIN_DURATION_SEC, MAX_DURATION_SEC)
        val delayMs = validDuration * 1000L

        Log.d(TAG, "Starting image timer for ${validDuration}s")

        currentTimerJob = scope.launch {
            try {
                // Wait for the specified duration
                delay(delayMs)

                // Execute callback if not cancelled
                if (isActive) {
                    Log.d(TAG, "Image timer expired, executing callback")
                    callback()
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "Image timer was cancelled")
                // Re-throw cancellation to properly handle coroutine cancellation
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error in image timer", e)
            }
        }
    }

    /**
     * Stops the current timer if running
     */
    fun stopTimer() {
        currentTimerJob?.let { job ->
            if (job.isActive) {
                Log.d(TAG, "Stopping image timer")
                job.cancel()
            }
        }
        currentTimerJob = null
    }

    /**
     * Checks if a timer is currently running
     */
    val isRunning: Boolean
        get() = currentTimerJob?.isActive == true

    /**
     * Cleanup method - should be called when the timer is no longer needed
     */
    fun cleanup() {
        Log.d(TAG, "Cleaning up image timer")
        stopTimer()
    }
}