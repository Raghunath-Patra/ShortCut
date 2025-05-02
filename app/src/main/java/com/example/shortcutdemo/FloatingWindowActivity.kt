////package com.example.shortcutdemo
////
////import android.app.Activity
////import android.content.Context
////import android.content.Intent
////import android.content.SharedPreferences
////import android.hardware.display.DisplayManager
////import android.hardware.display.VirtualDisplay
////import android.media.MediaRecorder
////import android.media.projection.MediaProjection
////import android.media.projection.MediaProjectionManager
////import android.net.Uri
////import android.os.Bundle
////import android.os.Environment
////import android.view.WindowManager
////import android.widget.Button
////import android.widget.Toast
////import androidx.activity.result.contract.ActivityResultContracts
////import androidx.appcompat.app.AppCompatActivity
////import java.io.File
////import java.text.SimpleDateFormat
////import java.util.Date
////import java.util.Locale
////import android.util.Log
////
////class FloatingWindowActivity : AppCompatActivity() {
////    private lateinit var startButton: Button
////    private lateinit var finishButton: Button
////    private lateinit var closeButton: Button
////
////    private var mediaProjectionManager: MediaProjectionManager? = null
////    private var mediaProjection: MediaProjection? = null
////    private var virtualDisplay: VirtualDisplay? = null
////    private var mediaRecorder: MediaRecorder? = null
////    private var isRecording = false
////    private var recordingFilePath: String? = null
////    private lateinit var sharedPreferences: SharedPreferences
////
////    companion object {
////        private const val PREFS_NAME = "ScreenRecordingPrefs"
////        private const val PREF_PERMISSION_GRANTED = "permission_granted"
////        private const val PREF_RESULT_CODE = "result_code"
////        private const val PREF_RESULT_DATA = "result_data"
////    }
////
////    private val mediaProjectionResultLauncher = registerForActivityResult(
////        ActivityResultContracts.StartActivityForResult()
////    ) { result ->
////        if (result.resultCode == Activity.RESULT_OK) {
////            // Save the permission result
////            result.data?.let { intent ->
////                sharedPreferences.edit().apply {
////                    putBoolean(PREF_PERMISSION_GRANTED, true)
////                    putInt(PREF_RESULT_CODE, result.resultCode)
////                    putString(PREF_RESULT_DATA, intent.toUri(Intent.URI_INTENT_SCHEME))
////                }.apply()
////
////                startScreenRecording(intent)
////            }
////        } else {
////            Toast.makeText(this, "Screen recording permission denied", Toast.LENGTH_SHORT).show()
////        }
////    }
////
////    override fun onCreate(savedInstanceState: Bundle?) {
////        super.onCreate(savedInstanceState)
////        supportActionBar?.hide()
////        setContentView(R.layout.activity_floating_window)
////
////        // Initialize SharedPreferences
////        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
////
////        // Set up floating window characteristics
////        setupFloatingWindow()
////
////        // Initialize screen recording components
////        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE)
////                as MediaProjectionManager
////
////        // Find buttons
////        startButton = findViewById(R.id.startButton)
////        finishButton = findViewById(R.id.finishButton)
////        closeButton = findViewById(R.id.closeButton)
////
////        // Set up button listeners
////        setupButtonListeners()
////    }
////
////    private fun setupFloatingWindow() {
////        window.setFlags(
////            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
////            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
////        )
////
////        window.setFlags(
////            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
////            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
////        )
////
////        val params = window.attributes
////        params.width = WindowManager.LayoutParams.WRAP_CONTENT
////        params.height = WindowManager.LayoutParams.WRAP_CONTENT
////        params.alpha = 0.9f
////        window.attributes = params
////    }
////
////    private fun setupButtonListeners() {
////        startButton.setOnClickListener {
////            if (!isRecording) {
////                // Check if permission was previously granted
////                val permissionGranted = sharedPreferences.getBoolean(PREF_PERMISSION_GRANTED, false)
////
////                if (permissionGranted) {
////                    // Retrieve saved permission data
////                    val resultCode = sharedPreferences.getInt(PREF_RESULT_CODE, Activity.RESULT_CANCELED)
////                    val resultData = sharedPreferences.getString(PREF_RESULT_DATA, null)
////
////                    if (resultCode == Activity.RESULT_OK && resultData != null) {
////                        // Reconstruct the intent from saved data
////                        val intent = Intent.parseUri(resultData, Intent.URI_INTENT_SCHEME)
////                        startScreenRecording(intent)
////                    } else {
////                        // If saved data is invalid, request permission again
////                        requestScreenRecordingPermission()
////                    }
////                } else {
////                    // Request permission for the first time
////                    requestScreenRecordingPermission()
////                }
////            } else {
////                Toast.makeText(this, "Recording already in progress", Toast.LENGTH_SHORT).show()
////            }
////        }
////
////        finishButton.setOnClickListener {
////            if (isRecording) {
////                stopScreenRecording()
////            } else {
////                Toast.makeText(this, "No recording in progress", Toast.LENGTH_SHORT).show()
////            }
////        }
////
////        closeButton.setOnClickListener {
////            // Stop recording if still in progress and close the window
//////            if (isRecording) {
//////                stopScreenRecording()
//////            }
////            finish()
////        }
////    }
////
////    private fun requestScreenRecordingPermission() {
////        mediaProjectionManager?.let { manager ->
////            val captureIntent = manager.createScreenCaptureIntent()
////            mediaProjectionResultLauncher.launch(captureIntent)
////        }
////    }
////
////    private fun startScreenRecording(intent: Intent) {
////        try {
////            // Prepare MediaRecorder
////            mediaRecorder = MediaRecorder().apply {
////                setVideoSource(MediaRecorder.VideoSource.SURFACE)
////                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
////                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
////                setVideoFrameRate(30)
////                setVideoEncodingBitRate(10_000_000)
////                setVideoSize(1280, 720)
////
////                // Create a unique file name
////                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
////                recordingFilePath = "${getExternalFilesDir(Environment.DIRECTORY_MOVIES)}/ScreenRecording_$timestamp.mp4"
////                setOutputFile(recordingFilePath)
////                prepare()
////            }
////
////            // Create MediaProjection
////            mediaProjection = mediaProjectionManager?.getMediaProjection(Activity.RESULT_OK, intent)
////
////            // Create Virtual Display
////            virtualDisplay = mediaProjection?.createVirtualDisplay(
////                "ScreenRecorder",
////                1280, 720,
////                resources.displayMetrics.densityDpi,
////                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
////                mediaRecorder?.surface,
////                null,
////                null
////            )
////
////            // Start recording
////            mediaRecorder?.start()
////            isRecording = true
////
////            Toast.makeText(this, "Screen recording started", Toast.LENGTH_SHORT).show()
////            startButton.isEnabled = false
////            finishButton.isEnabled = true
////        } catch (e: Exception) {
////            Toast.makeText(this, "Failed to start recording: ${e.message}", Toast.LENGTH_SHORT).show()
////            e.printStackTrace()
////        }
////    }
////
////    private fun stopScreenRecording() {
////        Log.d("ScreenRecording", "Is Recording: $isRecording, MediaRecorder State: ${mediaRecorder}")
////        try {
////            mediaRecorder?.apply {
////                stop()
////                reset()
////                release()
////            }
////
////            virtualDisplay?.release()
////            mediaProjection?.stop()
////
////            // Reset references
////            mediaRecorder = null
////            virtualDisplay = null
////            mediaProjection = null
////
////            isRecording = false
////
////            // Notify user and allow sharing
////            Toast.makeText(this, "Screen recording saved", Toast.LENGTH_SHORT).show()
////
////            // Optional: Share the recorded video
////            recordingFilePath?.let { path ->
////                val videoFile = File(path)
////                val videoUri = Uri.fromFile(videoFile)
////                val shareIntent = Intent(Intent.ACTION_SEND).apply {
////                    type = "video/mp4"
////                    putExtra(Intent.EXTRA_STREAM, videoUri)
////                }
////                startActivity(Intent.createChooser(shareIntent, "Share Screen Recording"))
////            }
////
////            startButton.isEnabled = true
////            finishButton.isEnabled = false
////        } catch (e: Exception) {
////            Toast.makeText(this, "Error stopping recording: ${e.message}", Toast.LENGTH_SHORT).show()
////            e.printStackTrace()
////        }
////    }
////
//////    override fun onDestroy() {
//////        // Ensure recording stops if activity is destroyed
//////        if (isRecording) {
//////            stopScreenRecording()
//////        }
//////        super.onDestroy()
//////    }
////}
////
////
////package com.example.shortcutdemo
////
////import android.os.Bundle
////import android.view.View
////import android.view.WindowManager
////import android.widget.Button
////import androidx.appcompat.app.AppCompatActivity
////
////class FloatingWindowActivity : AppCompatActivity() {
////
////    override fun onCreate(savedInstanceState: Bundle?) {
////        super.onCreate(savedInstanceState)
////        supportActionBar?.hide()
////        //actionBar?.hide()
////        setContentView(R.layout.activity_floating_window)
////
////        // Set up the window as a small floating window
////        window.setFlags(
////            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
////            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
////        )
////
////        window.setFlags(
////            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
////            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
////        )
////
////        // Resize the window to make it floating-like
////        val params = window.attributes
////        params.width = WindowManager.LayoutParams.WRAP_CONTENT
////        params.height = WindowManager.LayoutParams.WRAP_CONTENT
////        params.alpha = 0.9f  // Slight transparency
////        window.attributes = params
////
////        val closeButton = findViewById<Button>(R.id.closeButton)
////        closeButton.setOnClickListener {
////            finish()
////        }
////    }
////}
////
////package com.example.shortcutdemo
////
////import android.os.Bundle
////import android.view.View
////import android.content.Intent
////import android.view.WindowManager
////import android.widget.Button
////import androidx.appcompat.app.AppCompatActivity
////
////class FloatingWindowActivity : AppCompatActivity() {
////
////    private var isRecording = false
////
////    override fun onCreate(savedInstanceState: Bundle?) {
////        super.onCreate(savedInstanceState)
////        supportActionBar?.hide()
////        setContentView(R.layout.activity_floating_window)
////
////        // Set up the window as a small floating window
////        window.setFlags(
////            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
////            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
////        )
////        window.setFlags(
////            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
////            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
////        )
////
////        // Resize the window to make it floating-like
////        val params = window.attributes
////        params.width = WindowManager.LayoutParams.WRAP_CONTENT
////        params.height = WindowManager.LayoutParams.WRAP_CONTENT
////        params.alpha = 0.9f  // Slight transparency
////        window.attributes = params
////
////        val closeButton = findViewById<Button>(R.id.closeButton)
////        closeButton.setOnClickListener {
////            finish()
////        }
////
////        val startButton = findViewById<Button>(R.id.startButton)
////        val finishButton = findViewById<Button>(R.id.finishButton)
////
////        // Start or stop recording on button click
////        startButton.setOnClickListener {
////            if (!isRecording) {
////                startScreenRecording()
////            }
////        }
////
////        finishButton.setOnClickListener {
////            if (isRecording) {
////                stopScreenRecording()
////            }
////        }
////    }
////
////    private fun startScreenRecording() {
////        isRecording = true
////        val intent = Intent(this, ScreenRecordingService::class.java)
////        intent.action = "START_RECORDING"
////        startService(intent)
////    }
////
////    private fun stopScreenRecording() {
////        isRecording = false
////        val intent = Intent(this, ScreenRecordingService::class.java)
////        intent.action = "STOP_RECORDING"
////        startService(intent)
////    }
////}
//
//package com.example.shortcutdemo
//
//import android.content.SharedPreferences
//import android.os.Bundle
//import android.content.Intent
//import android.view.WindowManager
//import android.widget.Button
//import androidx.appcompat.app.AppCompatActivity
//import android.graphics.Color
//import android.widget.Toast
//import androidx.core.content.edit
//
//class FloatingWindowActivity : AppCompatActivity() {
//
//    private var isRecording = false
//    private lateinit var sharedPreferences: SharedPreferences
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        supportActionBar?.hide()
//        setContentView(R.layout.activity_floating_window)
//
//        // Initialize SharedPreferences
//        sharedPreferences = getSharedPreferences("app_preferences", MODE_PRIVATE)
//
//        // Retrieve the saved recording state
//        isRecording = sharedPreferences.getBoolean("isRecording", false)
//
//        // Set up the window as a small floating window
//        window.setFlags(
//            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
//            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
//        )
//        window.setFlags(
//            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
//            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
//        )
//
//        // Resize the window to make it floating-like
//        val params = window.attributes
//        params.width = WindowManager.LayoutParams.WRAP_CONTENT
//        params.height = WindowManager.LayoutParams.WRAP_CONTENT
//        params.alpha = 0.9f  // Slight transparency
//        window.attributes = params
//
//        val closeButton = findViewById<Button>(R.id.closeButton)
//        closeButton.setOnClickListener {
//            finish()
//        }
//
//        val startButton = findViewById<Button>(R.id.startButton)
//        val finishButton = findViewById<Button>(R.id.finishButton)
//
//        // Set initial UI state based on isRecording
//        updateButtonStates(startButton, finishButton)
//
//        // Start or stop recording on button click
//        startButton.setOnClickListener {
//            if (!isRecording) {
//                startScreenRecording(startButton, finishButton)
//                Toast.makeText(this, "Recording Started", Toast.LENGTH_SHORT).show()
//            }
//        }
//
//        finishButton.setOnClickListener {
//            if (isRecording) {
//                stopScreenRecording(startButton, finishButton)
//                Toast.makeText(this, "Recording Stopped", Toast.LENGTH_SHORT).show()
//            }
//        }
//    }
//
////    private fun startScreenRecording(startButton: Button, finishButton: Button) {
////        isRecording = true
////        // Save the state to SharedPreferences
////        sharedPreferences.edit() { putBoolean("isRecording", true) }
////
////        val intent = Intent(this, ScreenRecordingService::class.java)
////        intent.action = "START_RECORDING"
////        startService(intent)
////
////        // Update UI based on the recording state
////        updateButtonStates(startButton, finishButton)
////    }
////
////    private fun stopScreenRecording(startButton: Button, finishButton: Button) {
////        isRecording = false
////        // Save the state to SharedPreferences
////        sharedPreferences.edit() { putBoolean("isRecording", false) }
////
////        val intent = Intent(this, ScreenRecordingService::class.java)
////        intent.action = "STOP_RECORDING"
////        startService(intent)
////
////        // Update UI based on the recording state
////        updateButtonStates(startButton, finishButton)
////    }
//private fun startScreenRecording(startButton: Button, finishButton: Button) {
//    isRecording = true
//    // Save the state to SharedPreferences
//    sharedPreferences.edit { putBoolean("isRecording", true) }
//
//    // Start the transparent activity to request screen capture permission if needed
//    val captureIntent = Intent(this, ScreenCaptureActivity::class.java)
//    startActivity(captureIntent)
//
//    // Update UI based on the recording state
//    updateButtonStates(startButton, finishButton)
//}
//
//    private fun stopScreenRecording(startButton: Button, finishButton: Button) {
//        isRecording = false
//        // Save the state to SharedPreferences
//        sharedPreferences.edit { putBoolean("isRecording", false) }
//
//        // Stop the recording service
//        val intent = Intent(this, ScreenRecordingService::class.java)
//        intent.action = ScreenRecordingService.ACTION_STOP
//        startService(intent)
//
//        // Update UI based on the recording state
//        updateButtonStates(startButton, finishButton)
//    }
//
//    // Update button states (text, background color)
//    private fun updateButtonStates(startButton: Button, finishButton: Button) {
//        if (isRecording) {
//            // Change Start button to Stop and color to red
//            //startButton.text = "Stop"
//            startButton.alpha = 0.5F
//            finishButton.alpha = 1F
//            finishButton.isEnabled = true
//            startButton.isEnabled = false
////            startButton.setBackgroundColor(Color.RED)
////            startButton.setTextColor(Color.WHITE)
//
//            // Change Finish button to available state
//            //finishButton.visibility = Button.VISIBLE
//        } else {
//            // Reset Start button to Start and color to green
//            //startButton.text = "Start"
//            finishButton.alpha = 0.5F
//            startButton.alpha = 1F
//            finishButton.isEnabled = false
//            startButton.isEnabled = true
////            startButton.setBackgroundColor(Color.GREEN)
////            startButton.setTextColor(Color.WHITE)
////
////            // Hide Finish button if recording is stopped
////            //finishButton.visibility = Button.INVISIBLE
////
////            finishButton.setBackgroundColor(Color.RED)
////            finishButton.setTextColor(Color.WHITE)
//        }
//    }
//
//    override fun onPause() {
//        super.onPause()
//        // Optionally save the state in onPause() to handle scenarios when the app is sent to the background
//        sharedPreferences.edit() { putBoolean("isRecording", isRecording) }
//    }
//
//    override fun onResume() {
//        super.onResume()
//        // Retrieve the state when the activity is resumed
//        isRecording = sharedPreferences.getBoolean("isRecording", false)
//    }
//}

package com.example.shortcutdemo

import android.content.SharedPreferences
import android.os.Bundle
import android.content.Intent
import android.os.Build
import android.view.WindowManager
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import android.widget.Toast
import androidx.core.content.edit

class FloatingWindowActivity : AppCompatActivity() {

    private var isRecording = false
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_floating_window)

        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences(ScreenRecordingService.PREFS_NAME, MODE_PRIVATE)

        // Retrieve the saved recording state
        isRecording = sharedPreferences.getBoolean(ScreenRecordingService.KEY_IS_RECORDING, false)

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

        val startButton = findViewById<Button>(R.id.startButton)
        val finishButton = findViewById<Button>(R.id.finishButton)

        // Set initial UI state based on isRecording
        updateButtonStates(startButton, finishButton)

        // Start or stop recording on button click
        startButton.setOnClickListener {
            if (!isRecording) {
                startScreenRecording(startButton, finishButton)
                Toast.makeText(this, "Starting recording...", Toast.LENGTH_SHORT).show()
            }
        }

        finishButton.setOnClickListener {
            if (isRecording) {
                stopScreenRecording(startButton, finishButton)
                Toast.makeText(this, "Stopping recording...", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startScreenRecording(startButton: Button, finishButton: Button) {
        // Check if we already have permission
        val hasPermission = sharedPreferences.contains(ScreenRecordingService.KEY_RESULT_CODE) &&
                sharedPreferences.contains(ScreenRecordingService.KEY_DATA_INTENT)

        if (hasPermission) {
            // We already have permission, start the service directly
            val intent = Intent(this, ScreenRecordingService::class.java).apply {
                action = ScreenRecordingService.ACTION_START
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }

            // Set recording state to true - this will be confirmed by the service
            isRecording = true
            updateButtonStates(startButton, finishButton)

        } else {
            // We need to get permission first, so start the transparent activity
            val permissionIntent = Intent(this, ScreenCaptureRequestActivity::class.java).apply {
                putExtra("requestPermission", true)
            }
            startActivity(permissionIntent)

            // Note: The recording state will be updated when we check in onResume()
            // or when the user returns to this activity
        }
    }

    private fun stopScreenRecording(startButton: Button, finishButton: Button) {
        // Stop the recording service
        val intent = Intent(this, ScreenRecordingService::class.java).apply {
            action = ScreenRecordingService.ACTION_STOP
        }
        startService(intent)

        // Update our local state
        isRecording = false
        updateButtonStates(startButton, finishButton)
    }

    // Update button states (text, background color)
    private fun updateButtonStates(startButton: Button, finishButton: Button) {
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

    override fun onPause() {
        super.onPause()
        // We don't need to save isRecording here as the service manages this state
    }

    override fun onResume() {
        super.onResume()
        // Check the current recording state from the service's shared preferences
        isRecording = sharedPreferences.getBoolean(ScreenRecordingService.KEY_IS_RECORDING, false)

        // Get references to buttons and update UI
        val startButton = findViewById<Button>(R.id.startButton)
        val finishButton = findViewById<Button>(R.id.finishButton)
        updateButtonStates(startButton, finishButton)
    }
}
