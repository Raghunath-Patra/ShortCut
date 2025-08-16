package com.example.shortcutdemo

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import android.widget.Toast
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

            Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
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
                startScreenRecording()
            }
        }

        finishButton.setOnClickListener {
            if (isRecording) {
                stopScreenRecording()
                Toast.makeText(this, "Stopping recording...", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startScreenRecording() {
        screenRecordLauncher.launch(
            mediaProjectionManager.createScreenCaptureIntent()
        )
    }

    private fun stopScreenRecording() {
        val serviceIntent = Intent(
            applicationContext,
            ScreenRecordService::class.java
        ).apply {
            action = STOP_RECORDING
        }
        startService(serviceIntent)
    }

    private fun updateButtonStates() {
        if (isRecording) {
            // Disable Start button, enable Finish button
            startButton.alpha = 0.5F
            finishButton.alpha = 1F
            finishButton.isEnabled = true
            startButton.isEnabled = false
        } else {
            // Enable Start button, disable Finish button
            finishButton.alpha = 0.5F
            startButton.alpha = 1F
            finishButton.isEnabled = false
            startButton.isEnabled = true
        }
    }

    override fun onResume() {
        super.onResume()
        // Update button states when activity resumes
        updateButtonStates()
    }

    override fun onDestroy() {
        super.onDestroy()

        // Notify the service that the window is closing
        val serviceIntent = Intent(this, FloatingButtonService::class.java)
        serviceIntent.putExtra("window_closed", true)
        startService(serviceIntent)

        Log.d("FloatingWindowActivity", "Window destroyed, notified service")
    }
}