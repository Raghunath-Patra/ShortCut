package com.example.shortcutdemo

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.lifecycleScope
import com.example.shortcutdemo.ScreenRecordService.Companion.KEY_RECORDING_CONFIG
import com.example.shortcutdemo.ScreenRecordService.Companion.START_RECORDING
import com.example.shortcutdemo.ScreenRecordService.Companion.STOP_RECORDING
import kotlinx.coroutines.launch

class FloatingWindowActivity : AppCompatActivity() {

    private var isRecording = false
    private lateinit var startButton: Button
    private lateinit var finishButton: Button

    private val mediaProjectionManager by lazy {
        getSystemService<MediaProjectionManager>()!!
    }

    private val screenRecordLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val intent = result.data ?: return@registerForActivityResult
            
            // Check if audio permission is granted before starting
            val hasAudioPermission = ContextCompat.checkSelfPermission(
                this, 
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasAudioPermission) {
                Toast.makeText(this, "Audio permission required for system audio capture", Toast.LENGTH_LONG).show()
                requestAudioPermission()
                return@registerForActivityResult
            }

            val config = ScreenRecordConfig(
                resultCode = result.resultCode,
                data = intent
            )

            val serviceIntent = Intent(
                applicationContext,
                ScreenRecordService::class.java
            ).apply {
                action = START_RECORDING
                putExtra(KEY_RECORDING_CONFIG, config)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }

            Toast.makeText(this, "Recording started with system audio", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "Screen recording with system audio started")
        } else {
            Toast.makeText(this, "Screen capture permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(this, "Audio permission granted. You can now record with system audio.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Audio permission denied. Recording will be video only.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_floating_window)

        // Set up the window as a small floating window
        window.setFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        )
        window.setFlags(
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        )

        // Resize the window to make it floating-like
        val params = window.attributes
        params.width = WindowManager.LayoutParams.WRAP_CONTENT
        params.height = WindowManager.LayoutParams.WRAP_CONTENT
        params.alpha = 0.9f  // Slight transparency
        window.attributes = params

        val closeButton = findViewById<Button>(R.id.closeButton)
        closeButton.setOnClickListener {
            finish()
        }

        startButton = findViewById<Button>(R.id.startButton)
        finishButton = findViewById<Button>(R.id.finishButton)

        // Observe the recording state
        lifecycleScope.launch {
            ScreenRecordService.isServiceRunning.collect { serviceRunning ->
                isRecording = serviceRunning
                updateButtonStates()
            }
        }

        // Start recording on button click
        startButton.setOnClickListener {
            if (!isRecording) {
                checkPermissionsAndStartRecording()
            }
        }

        finishButton.setOnClickListener {
            if (isRecording) {
                stopScreenRecording()
                Toast.makeText(this, "Stopping recording...", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkPermissionsAndStartRecording() {
        val hasAudioPermission = ContextCompat.checkSelfPermission(
            this, 
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasAudioPermission) {
            Toast.makeText(this, "Requesting audio permission for system audio capture...", Toast.LENGTH_SHORT).show()
            requestAudioPermission()
        } else {
            startScreenRecording()
        }
    }

    private fun requestAudioPermission() {
        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun startScreenRecording() {
        try {
            val captureIntent = mediaProjectionManager.createScreenCaptureIntent()
            screenRecordLauncher.launch(captureIntent)
            Log.d(TAG, "Screen capture intent launched")
        } catch (e: Exception) {
            Log.e(TAG, "Error launching screen capture", e)
            Toast.makeText(this, "Error starting screen capture: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopScreenRecording() {
        try {
            val serviceIntent = Intent(
                applicationContext,
                ScreenRecordService::class.java
            ).apply {
                action = STOP_RECORDING
            }
            startService(serviceIntent)
            Log.d(TAG, "Stop recording intent sent")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
            Toast.makeText(this, "Error stopping recording: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateButtonStates() {
        if (isRecording) {
            // Disable Start button, enable Finish button
            startButton.alpha = 0.5F
            finishButton.alpha = 1F
            finishButton.isEnabled = true
            startButton.isEnabled = false
            startButton.text = "Recording..."
        } else {
            // Enable Start button, disable Finish button
            finishButton.alpha = 0.5F
            startButton.alpha = 1F
            finishButton.isEnabled = false
            startButton.isEnabled = true
            startButton.text = "Start Recording"
        }
    }

    override fun onResume() {
        super.onResume()
        // Update button states when activity resumes
        updateButtonStates()
        
        // Log current permission status
        val hasAudioPermission = ContextCompat.checkSelfPermission(
            this, 
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        Log.d(TAG, "Audio permission status on resume: $hasAudioPermission")
    }

    override fun onDestroy() {
        super.onDestroy()

        // Notify the service that the window is closing
        val serviceIntent = Intent(this, FloatingButtonService::class.java)
        serviceIntent.putExtra("window_closed", true)
        startService(serviceIntent)

        Log.d(TAG, "Window destroyed, notified service")
    }

    companion object {
        private const val TAG = "FloatingWindowActivity"
    }
}