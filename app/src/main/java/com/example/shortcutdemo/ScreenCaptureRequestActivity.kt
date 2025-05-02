package com.example.shortcutdemo

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast

/**
 * Transparent activity that handles requesting screen capture permission
 * and passes the result back to the ScreenRecordingService
 */
class ScreenCaptureRequestActivity : Activity() {

    private val TAG = "ScreenCaptureRequest"
    private val PERMISSION_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request screen capture permission immediately
        requestScreenCapture()
    }

    private fun requestScreenCapture() {
        val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(
            mediaProjectionManager.createScreenCaptureIntent(),
            PERMISSION_CODE
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PERMISSION_CODE) {
            if (resultCode == RESULT_OK && data != null) {
                // Pass the permission data to the service
                val serviceIntent = Intent(this, ScreenRecordingService::class.java).apply {
                    action = ScreenRecordingService.ACTION_SET_PERMISSION_DATA
                    putExtra("resultCode", resultCode)
                    putExtra("data", data)
                    putExtra("startRecording", true)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }

                Toast.makeText(this, "Screen recording started", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Screen recording permission denied", Toast.LENGTH_SHORT).show()

                // Make sure to update shared preferences to indicate we're not recording
                val sharedPreferences = getSharedPreferences(ScreenRecordingService.PREFS_NAME, MODE_PRIVATE)
                sharedPreferences.edit().putBoolean(ScreenRecordingService.KEY_IS_RECORDING, false).apply()
            }
        }

        // Always finish the activity when done
        finish()
    }
}