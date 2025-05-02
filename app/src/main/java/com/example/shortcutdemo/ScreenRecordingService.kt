////
////package com.example.shortcutdemo
////
////import android.app.Service
////import android.content.Intent
////import android.media.projection.MediaProjection
////import android.media.projection.MediaProjectionManager
////import android.os.IBinder
////import android.util.Log
////import android.app.Activity
////import android.content.Context
////import android.hardware.display.DisplayManager
////import android.hardware.display.VirtualDisplay
////import android.widget.Toast
////
////class ScreenRecordingService : Service() {
////
////    private lateinit var mediaProjectionManager: MediaProjectionManager
////    private var mediaProjection: MediaProjection? = null
////    private var virtualDisplay: VirtualDisplay? = null
////    private lateinit var screenRecorder: ScreenRecorder
////
////    override fun onCreate() {
////        super.onCreate()
////        Log.d("ScreenRecordingService", "Service Created")
////
////        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
////    }
////
////    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
////        val action = intent?.action
////        when (action) {
////            "START_RECORDING" -> {
////                val resultCode = intent.getIntExtra("resultCode", Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
////                val data = intent.getParcelableExtra<Intent>("data")
////                //val data = intent.getParcelable<Intent>("data")
////
////
////                if (resultCode == Activity.RESULT_OK && data != null) {
////                    // Initialize the MediaProjection using the result from the activity
////                    mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)
////
////                    // Start screen recording
////                    startVirtualDisplay()
////                    startScreenRecording()
////                } else if(data == null){
////                    Toast.makeText(this, "Permission Denied serv", Toast.LENGTH_SHORT).show()
////                }else{
////                    Toast.makeText(this, "Permission Denied service", Toast.LENGTH_SHORT).show()
////
////                }
////            }
////            "STOP_RECORDING" -> stopRecording()
////        }
////        return START_STICKY
////    }
////
////    private fun startVirtualDisplay() {
////        val metrics = resources.displayMetrics
////        virtualDisplay = mediaProjection?.createVirtualDisplay(
////            "ScreenRecording",
////            metrics.widthPixels,
////            metrics.heightPixels,
////            metrics.densityDpi,
////            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
////            screenRecorder.surface,
////            null,
////            null
////        )
////    }
////
////    private fun startScreenRecording() {
////        screenRecorder = ScreenRecorder(this)
////        screenRecorder.startRecording()
////    }
////
////    private fun stopRecording() {
////        mediaProjection?.stop()
////        virtualDisplay?.release()
////        screenRecorder.stopRecording()
////        Log.d("ScreenRecordingService", "Recording stopped")
////    }
////
////    override fun onBind(intent: Intent?): IBinder? {
////        return null
////    }
////
////    override fun onDestroy() {
////        super.onDestroy()
////        mediaProjection?.stop()
////        virtualDisplay?.release()
////        screenRecorder.stopRecording()
////        Log.d("ScreenRecordingService", "Service Destroyed")
////    }
////}
//
//
//
////package com.example.shortcutdemo
////
////import android.app.Service
////import android.content.Intent
////import android.media.projection.MediaProjection
////import android.media.projection.MediaProjectionManager
////import android.os.IBinder
////import android.util.Log
////import android.app.Activity
////import android.content.Context
////import android.content.pm.PackageManager
////import android.hardware.display.VirtualDisplay
////import android.widget.Toast
////import android.os.Build
////import android.os.Handler
////import android.os.Looper
////import androidx.core.app.ActivityCompat
////import androidx.core.content.ContextCompat
////
////class ScreenRecordingService : Service() {
////
////    private lateinit var mediaProjectionManager: MediaProjectionManager
////    private var mediaProjection: MediaProjection? = null
////    private var virtualDisplay: VirtualDisplay? = null
////    private lateinit var screenRecorder: ScreenRecorder  // You would define this class to handle the actual recording
////
////    override fun onCreate() {
////        super.onCreate()
////        Log.d("ScreenRecordingService", "Service Created")
////
////        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
////    }
////
////    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
////        val action = intent?.action
////        when (action) {
////            "START_RECORDING" -> startRecording()
////            "STOP_RECORDING" -> stopRecording()
////        }
////        return START_STICKY
////    }
////
////    private fun startRecording() {
////        // Request permission for screen capture
////        val captureIntent = mediaProjectionManager.createScreenCaptureIntent()
////        captureIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
////        startActivityForResult(captureIntent, REQUEST_CODE_SCREEN_CAPTURE)
////        Log.d("ScreenRecordingService", "Recording started")
////    }
////
////    private fun stopRecording() {
////        mediaProjection?.stop()
////        virtualDisplay?.release()
////        screenRecorder.stopRecording()
////        Log.d("ScreenRecordingService", "Recording stopped")
////    }
////
////    private fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
////        if (requestCode == REQUEST_CODE_SCREEN_CAPTURE) {
////            if (resultCode == Activity.RESULT_OK && data != null) {
////                // Initialize the MediaProjection
////                mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)
////
////                // Start screen recording using MediaProjection
////                startVirtualDisplay()
////                startScreenRecording()
////            } else {
////                Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show()
////            }
////        }
////    }
////
////    private fun startVirtualDisplay() {
////        val metrics = resources.displayMetrics
////        virtualDisplay = mediaProjection?.createVirtualDisplay(
////            "ScreenRecording",
////            metrics.widthPixels,
////            metrics.heightPixels,
////            metrics.densityDpi,
////            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
////            screenRecorder.surface,
////            null,
////            null
////        )
////    }
////
////    private fun startScreenRecording() {
////        screenRecorder = ScreenRecorder(this)
////        screenRecorder.startRecording()
////    }
////
////    override fun onBind(intent: Intent?): IBinder? {
////        return null
////    }
////
////    override fun onDestroy() {
////        super.onDestroy()
////        mediaProjection?.stop()
////        virtualDisplay?.release()
////        screenRecorder.stopRecording()
////        Log.d("ScreenRecordingService", "Service Destroyed")
////    }
////
////    companion object {
////        const val REQUEST_CODE_SCREEN_CAPTURE = 1
////    }
////}
////
////
////package com.example.shortcutdemo --------------------------------------
////
////import android.app.Service
////import android.content.Intent
////import android.os.IBinder
////import android.util.Log
////
////class ScreenRecordingService : Service() {
////
////
////    override fun onCreate() {
////        super.onCreate()
////        // Initialize the screen recording functionality here
////        Log.d("ScreenRecordingService", "Service Created")
////    }
////
////    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
////        // Handle start/stop commands from the activity
////        val action = intent?.action
////        when (action) {
////            "START_RECORDING" -> startRecording()
////            "STOP_RECORDING" -> stopRecording()
////        }
////        return START_STICKY
////    }
////
////    private fun startRecording() {
////
////            Log.d("ScreenRecordingService", "Recording started")
////
////    }
////
////    private fun stopRecording() {
////
////            Log.d("ScreenRecordingService", "Recording stopped")
////
////    }
////
////    override fun onBind(intent: Intent?): IBinder? {
////        return null
////    }
////
////    override fun onDestroy() {
////        super.onDestroy()
////        // Release any resources when the service is destroyed
////        Log.d("ScreenRecordingService", "Service Destroyed")
////    }
////}
//
//
//package com.example.shortcutdemo
//
//import android.app.Notification
//import android.app.NotificationChannel
//import android.app.NotificationManager
//import android.app.PendingIntent
//import android.app.Service
//import android.content.Context
//import android.content.Intent
//import android.content.SharedPreferences
//import android.hardware.display.DisplayManager
//import android.hardware.display.VirtualDisplay
//import android.media.MediaRecorder
//import android.media.projection.MediaProjection
//import android.media.projection.MediaProjectionManager
//import android.os.Build
//import android.os.Environment
//import android.os.IBinder
//import android.util.DisplayMetrics
//import android.util.Log
//import android.view.WindowManager
//import androidx.core.app.NotificationCompat
//import java.io.File
//import java.io.IOException
//import java.text.SimpleDateFormat
//import java.util.Date
//import java.util.Locale
//
//class ScreenRecordingService : Service() {
//
//    private var mediaProjection: MediaProjection? = null
//    private var virtualDisplay: VirtualDisplay? = null
//    private var mediaRecorder: MediaRecorder? = null
//    private var isRecording = false
//    private lateinit var mediaProjectionManager: MediaProjectionManager
//    private lateinit var sharedPreferences: SharedPreferences
//    private var screenWidth = 1280
//    private var screenHeight = 720
//    private var screenDensity = 1
//    private var recordingFile: File? = null
//
//    companion object {
//        private const val TAG = "ScreenRecordingService"
//        private const val NOTIFICATION_ID = 5678
//        private const val CHANNEL_ID = "screen_recording_channel"
//        private const val PERMISSION_CODE = 1001
//
//        // Intent actions
//        const val ACTION_START = "START_RECORDING"
//        const val ACTION_STOP = "STOP_RECORDING"
//
//        // SharedPreferences constants
//        const val PREFS_NAME = "app_preferences"
//        const val KEY_RESULT_CODE = "result_code"
//        const val KEY_DATA_INTENT = "data_intent"
//    }
//
//    override fun onCreate() {
//        super.onCreate()
//        Log.d(TAG, "Service created")
//
//        // Initialize shared preferences
//        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
//
//        // Get screen metrics
//        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
//        val metrics = DisplayMetrics()
//        windowManager.defaultDisplay.getMetrics(metrics)
//        screenWidth = metrics.widthPixels
//        screenHeight = metrics.heightPixels
//        screenDensity = metrics.densityDpi
//
//        isRecording = sharedPreferences.getBoolean("isRecording", true)
//        // Initialize MediaProjectionManager
//        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
//
//        createNotificationChannel()
//    }
//
//    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
//        when (intent?.action) {
//            ACTION_START -> {
//
//                    startRecording()
//
//            }
//            ACTION_STOP -> {
//
//                    stopRecording()
//
//            }
//        }
//        return START_STICKY
//    }
//
//    private fun startRecording() {
//        Log.d(TAG, "Starting recording...")
//
//        if (prepareMediaRecorder()) {
//            // Get the result code and data intent from SharedPreferences
//            val resultCode = sharedPreferences.getInt(KEY_RESULT_CODE, -1)
//            val dataIntent = sharedPreferences.getString(KEY_DATA_INTENT, null)
//
//            if (resultCode != -1 && dataIntent != null) {
//                // Recreate the intent from the saved string
//                val intent = Intent.parseUri(dataIntent, 0)
//
//                // Get the MediaProjection
//                mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, intent)
//
//                if (mediaProjection != null) {
//                    // Create virtual display
//                    createVirtualDisplay()
//
//                    // Start recording
//                    try {
//                        mediaRecorder?.start()
//                        isRecording = true
//
//                        // Show notification
//                        startForeground(NOTIFICATION_ID, createNotification("Recording in progress"))
//                        Log.d(TAG, "Recording started")
//                    } catch (e: Exception) {
//                        Log.e(TAG, "Failed to start MediaRecorder", e)
//                        releaseRecorder()
//                    }
//                } else {
//                    Log.e(TAG, "Failed to get MediaProjection")
//                }
//            } else {
//                // We need to get permission first
//                Log.d(TAG, "No permission data found")
//                // This would typically be handled by a transparent activity to request permission
//                // For this demo, we'll just stop the service
//                // --------------- change is needed here i g
//                stopSelf()
//            }
//        }
//    }
//
//    private fun stopRecording() {
//        Log.d(TAG, "Stopping recording...")
//
//        if (isRecording) {
//            try {
//                mediaRecorder?.stop()
//                mediaRecorder?.reset()
//                Log.d(TAG, "Recording stopped")
//            } catch (e: Exception) {
//                Log.e(TAG, "Error stopping recording", e)
//                // Even if there's an error, we'll continue with cleanup
//            } finally {
//                isRecording = false
//                releaseRecorder()
//
//                // Update notification
//                val notification = createNotification("Recording saved")
//                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
//                notificationManager.notify(NOTIFICATION_ID, notification)
//
//                // Stop foreground service but keep notification
//                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
//                    stopForeground(Service.STOP_FOREGROUND_DETACH)
//                } else {
//                    stopForeground(false)
//                }
//
//                // Allow the service to be stopped after a delay
//                stopSelf()
//            }
//        }
//    }
//
//    private fun createVirtualDisplay() {
//        virtualDisplay = mediaProjection?.createVirtualDisplay(
//            "ScreenRecording",
//            screenWidth,
//            screenHeight,
//            screenDensity,
//            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
//            mediaRecorder?.surface,
//            null,
//            null
//        )
//    }
//
//    private fun prepareMediaRecorder(): Boolean {
//        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
//            MediaRecorder(this)
//        } else {
//            @Suppress("DEPRECATION")
//            MediaRecorder()
//        }
//
//        try {
//            // Create output file
//            val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
//            val fileName = "ScreenRecording_${dateFormat.format(Date())}.mp4"
//            val storageDir = getExternalFilesDir(Environment.DIRECTORY_MOVIES)
//            recordingFile = File(storageDir, fileName)
//
//            mediaRecorder?.apply {
//                setVideoSource(MediaRecorder.VideoSource.SURFACE)
//                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
//                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
//                setVideoEncodingBitRate(8 * 1000 * 1000)
//                setVideoFrameRate(30)
//                setVideoSize(screenWidth, screenHeight)
//                setOutputFile(recordingFile?.absolutePath)
//                prepare()
//            }
//            return true
//        } catch (e: IOException) {
//            Log.e(TAG, "Error preparing MediaRecorder", e)
//            releaseRecorder()
//            return false
//        }
//    }
//
//    private fun releaseRecorder() {
//        virtualDisplay?.release()
//        mediaProjection?.stop()
//        mediaRecorder?.release()
//
//        virtualDisplay = null
//        mediaProjection = null
//        mediaRecorder = null
//    }
//
//    private fun createNotificationChannel() {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            val channel = NotificationChannel(
//                CHANNEL_ID,
//                "Screen Recording Service",
//                NotificationManager.IMPORTANCE_DEFAULT
//            ).apply {
//                description = "Used for the screen recording service"
//                setSound(null, null)
//                enableVibration(false)
//            }
//
//            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
//            notificationManager.createNotificationChannel(channel)
//        }
//    }
//
//    private fun createNotification(contentText: String): Notification {
//        // Create a pending intent for the notification
//        val openAppIntent = Intent(this, MainActivity::class.java)
//        val pendingIntent = PendingIntent.getActivity(
//            this,
//            0,
//            openAppIntent,
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
//            } else {
//                PendingIntent.FLAG_UPDATE_CURRENT
//            }
//        )
//
//        return NotificationCompat.Builder(this, CHANNEL_ID)
//            .setContentTitle("Screen Recording")
//            .setContentText(contentText)
//            .setSmallIcon(R.drawable.ic_shortcut)
//            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
//            .setContentIntent(pendingIntent)
//            .setOngoing(isRecording)
//            .build()
//    }
//
//    override fun onBind(intent: Intent?): IBinder? {
//        return null
//    }
//
//    override fun onDestroy() {
//        if (isRecording) {
//            stopRecording()
//        }
//        super.onDestroy()
//    }
//}

package com.example.shortcutdemo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScreenRecordingService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private lateinit var sharedPreferences: SharedPreferences
    private var screenWidth = 1280
    private var screenHeight = 720
    private var screenDensity = 1
    private var recordingFile: File? = null

    companion object {
        private const val TAG = "ScreenRecordingService"
        private const val NOTIFICATION_ID = 5678
        private const val CHANNEL_ID = "screen_recording_channel"
        private const val PERMISSION_CODE = 1001

        // Intent actions
        const val ACTION_START = "START_RECORDING"
        const val ACTION_STOP = "STOP_RECORDING"
        const val ACTION_SET_PERMISSION_DATA = "SET_PERMISSION_DATA"

        // SharedPreferences constants
        const val PREFS_NAME = "app_preferences"
        const val KEY_RESULT_CODE = "result_code"
        const val KEY_DATA_INTENT = "data_intent"
        const val KEY_IS_RECORDING = "is_recording"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")

        // Initialize shared preferences
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        // Get screen metrics
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        screenDensity = metrics.densityDpi

        // Correctly initialize recording state
        isRecording = sharedPreferences.getBoolean(KEY_IS_RECORDING, false)

        // Initialize MediaProjectionManager
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                if (!isRecording) {
                    startRecording()
                } else {
                    Log.d(TAG, "Recording already in progress")
                }
            }
            ACTION_STOP -> {
                if (isRecording) {
                    stopRecording()
                } else {
                    Log.d(TAG, "No recording in progress to stop")
                }
            }
            ACTION_SET_PERMISSION_DATA -> {
                // Save permission data
                val resultCode = intent.getIntExtra("resultCode", -1)
                val data = intent.getParcelableExtra<Intent>("data")

                if (resultCode != -1 && data != null) {
                    savePermissionData(resultCode, data)
                    Log.d(TAG, "Permission data saved successfully")

                    // Auto-start recording if that was the original intent
                    if (intent.getBooleanExtra("startRecording", false)) {
                        startRecording()
                    }
                } else {
                    Log.e(TAG, "Invalid permission data received")
                }
            }
        }
        return START_STICKY
    }

    private fun savePermissionData(resultCode: Int, data: Intent) {
        val editor = sharedPreferences.edit()
        editor.putInt(KEY_RESULT_CODE, resultCode)
        editor.putString(KEY_DATA_INTENT, data.toUri(0))
        editor.apply()
    }

    private fun startRecording() {
        Log.d(TAG, "Starting recording...")

        if (prepareMediaRecorder()) {
            // Get the result code and data intent from SharedPreferences
            val resultCode = sharedPreferences.getInt(KEY_RESULT_CODE, -1)
            val dataIntent = sharedPreferences.getString(KEY_DATA_INTENT, null)

            if (resultCode != -1 && dataIntent != null) {
                try {
                    // Recreate the intent from the saved string
                    val intent = Intent.parseUri(dataIntent, 0)

                    // Get the MediaProjection
                    mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, intent)

                    if (mediaProjection != null) {
                        // Create virtual display
                        createVirtualDisplay()

                        // Start recording
                        mediaRecorder?.start()
                        isRecording = true

                        // Save recording state
                        sharedPreferences.edit().putBoolean(KEY_IS_RECORDING, true).apply()

                        // Show notification
                        startForeground(NOTIFICATION_ID, createNotification("Recording in progress"))
                        Log.d(TAG, "Recording started successfully")
                    } else {
                        Log.e(TAG, "Failed to get MediaProjection")
                        onRecordingError("Failed to initialize media projection")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error starting recording", e)
                    onRecordingError("Failed to start recording: ${e.message}")
                }
            } else {
                // We need to get permission first
                Log.d(TAG, "No permission data found - need to request screen capture permission")

                // Send broadcast to request permission via an activity
                val requestPermIntent = Intent("com.example.shortcutdemo.REQUEST_SCREEN_CAPTURE")
                requestPermIntent.setPackage(packageName)
                sendBroadcast(requestPermIntent)

                // Note: Your MainActivity or a transparent activity should respond to this broadcast
                // and start the permission request process
            }
        } else {
            Log.e(TAG, "Failed to prepare MediaRecorder")
            onRecordingError("Failed to prepare recorder")
        }
    }

    private fun onRecordingError(errorMessage: String) {
        // Clean up resources
        releaseRecorder()
        isRecording = false
        sharedPreferences.edit().putBoolean(KEY_IS_RECORDING, false).apply()

        // Notify user
        val notification = createNotification("Recording error: $errorMessage")
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)

        // Stop foreground but keep notification
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_DETACH)
        } else {
            stopForeground(false)
        }
    }

    private fun stopRecording() {
        Log.d(TAG, "Stopping recording...")

        if (isRecording) {
            try {
                mediaRecorder?.stop()
                mediaRecorder?.reset()
                Log.d(TAG, "Recording stopped successfully")

                // Update recording state
                isRecording = false
                sharedPreferences.edit().putBoolean(KEY_IS_RECORDING, false).apply()

                // Show success notification with file path
                val notificationText = "Recording saved: ${recordingFile?.name}"
                val notification = createNotification(notificationText)
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(NOTIFICATION_ID, notification)

            } catch (e: Exception) {
                Log.e(TAG, "Error stopping recording", e)
                // Even if there's an error, we'll continue with cleanup
                isRecording = false
                sharedPreferences.edit().putBoolean(KEY_IS_RECORDING, false).apply()

                val notification = createNotification("Error while saving recording")
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(NOTIFICATION_ID, notification)
            } finally {
                releaseRecorder()

                // Stop foreground service but keep notification
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_DETACH)
                } else {
                    stopForeground(false)
                }

                // Allow the service to be stopped after a delay
                stopSelf()
            }
        }
    }

    private fun createVirtualDisplay() {
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenRecording",
            screenWidth,
            screenHeight,
            screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            mediaRecorder?.surface,
            null,
            null
        )
    }

//    private fun prepareMediaRecorder(): Boolean {
//        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
//            MediaRecorder(this)
//        } else {
//            @Suppress("DEPRECATION")
//            MediaRecorder()
//        }
//
//        try {
//            // Create output file
//            val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
//            val fileName = "ScreenRecording_${dateFormat.format(Date())}.mp4"
//            val storageDir = getExternalFilesDir(Environment.DIRECTORY_MOVIES)
//            recordingFile = File(storageDir, fileName)
//
//            mediaRecorder?.apply {
//                setVideoSource(MediaRecorder.VideoSource.SURFACE)
//                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
//                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
//                setVideoEncodingBitRate(8 * 1000 * 1000)
//                setVideoFrameRate(30)
//                setVideoSize(screenWidth, screenHeight)
//                setOutputFile(recordingFile?.absolutePath)
//                prepare()
//            }
//            return true
//        } catch (e: IOException) {
//            Log.e(TAG, "Error preparing MediaRecorder", e)
//            releaseRecorder()
//            return false
//        }
//    }
    // Replace the prepareMediaRecorder() method in ScreenRecordingService.kt with this updated version:

    private fun prepareMediaRecorder(): Boolean {
        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        try {
            // Create output file in app's internal files directory
            // This doesn't require storage permissions
            val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
            val fileName = "ScreenRecording_${dateFormat.format(Date())}.mp4"

            // Use app's internal storage instead of external storage
            val storageDir = getFilesDir()
            recordingFile = File(storageDir, fileName)

            mediaRecorder?.apply {
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setVideoEncodingBitRate(8 * 1000 * 1000)
                setVideoFrameRate(30)
                setVideoSize(screenWidth, screenHeight)
                setOutputFile(recordingFile?.absolutePath)
                prepare()
            }
            return true
        } catch (e: IOException) {
            Log.e(TAG, "Error preparing MediaRecorder", e)
            releaseRecorder()
            return false
        }
    }

    private fun releaseRecorder() {
        virtualDisplay?.release()
        mediaProjection?.stop()
        mediaRecorder?.release()

        virtualDisplay = null
        mediaProjection = null
        mediaRecorder = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Screen Recording Service",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Used for the screen recording service"
                setSound(null, null)
                enableVibration(false)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(contentText: String): Notification {
        // Create a pending intent for the notification
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        // Create stop action
        val stopIntent = Intent(this, ScreenRecordingService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Screen Recording")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_shortcut)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setOngoing(isRecording)

        // Add stop action only if recording is in progress
        if (isRecording) {
            builder.addAction(android.R.drawable.ic_media_pause, "Stop", stopPendingIntent)
        }

        return builder.build()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        if (isRecording) {
            stopRecording()
        }
        super.onDestroy()
    }
}