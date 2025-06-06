package com.example.shortcutdemo

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    companion object {
        private const val OVERLAY_PERMISSION_REQ_CODE = 1234
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_main)

        val createShortcutBtn = findViewById<Button>(R.id.createShortcutBtn)
        val removeShortcutBtn = findViewById<Button>(R.id.removeShortcutBtn)

        // Check and log permission status
        logPermissionStatus()

        createShortcutBtn.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                requestOverlayPermission()
            } else {
                startFloatingButtonService()
            }
        }

        removeShortcutBtn.setOnClickListener {
            stopFloatingButtonService()
        }
    }

    private fun logPermissionStatus() {
        val hasOverlayPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
        Log.d(TAG, "Overlay permission status: $hasOverlayPermission")
    }

    private fun requestOverlayPermission() {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            intent.data = Uri.parse("package:$packageName")
            startActivityForResult(intent, OVERLAY_PERMISSION_REQ_CODE)
            Toast.makeText(this, "Please grant the 'Display over other apps' permission", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error opening permission screen: ${e.message}")
            Toast.makeText(this, "Error requesting permission: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun startFloatingButtonService() {
        try {
            val serviceIntent = Intent(this, FloatingButtonService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            Toast.makeText(this, "Floating shortcut created", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "FloatingButtonService started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting FloatingButtonService: ${e.message}")
            Toast.makeText(this, "Error starting service: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopFloatingButtonService() {
        try {
            // Stop the floating button service
            stopService(Intent(this, FloatingButtonService::class.java))

            // Also stop any running screen recording service
            //stopService(Intent(this, ScreenRecordingService::class.java))
            stopService(Intent(this, ScreenRecordService::class.java))

            Toast.makeText(this, "Floating shortcut removed", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "Services stopped successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping services: ${e.message}")
            Toast.makeText(this, "Error stopping services: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        logPermissionStatus()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_PERMISSION_REQ_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val hasPermission = Settings.canDrawOverlays(this)
                Log.d(TAG, "Overlay permission after request: $hasPermission")

                if (hasPermission) {
                    startFloatingButtonService()
                } else {
                    Toast.makeText(this, "Permission denied. The floating shortcut won't work without this permission.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}

// Extension functions for file upload (if needed for future features, can be removed if not used)
fun uploadVideoFile(context: Context, videoPath: String) {
    // This function can be implemented later if needed for uploading recordings
    Log.d("MainActivity", "Upload functionality called for: $videoPath")
}

fun saveTranscriptLocally(context: Context, text: String?) {
    // This function can be implemented later if needed for transcription
    Log.d("MainActivity", "Save transcript functionality called")
}