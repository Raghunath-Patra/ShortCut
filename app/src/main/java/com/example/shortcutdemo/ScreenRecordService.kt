package com.example.shortcutdemo

import android.Manifest
import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.getSystemService
import androidx.window.layout.WindowMetricsCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

class ScreenRecordService: Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var audioRecord: AudioRecord? = null
    private var audioRecordingJob: Job? = null
    private var isAudioRecording = false
    
    private val mediaRecorder by lazy {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(applicationContext)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
    }
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val videoOutputFile by lazy {
        File(cacheDir, "screen_video_${System.currentTimeMillis()}.mp4")
    }
    
    private val audioOutputFile by lazy {
        File(cacheDir, "screen_audio_${System.currentTimeMillis()}.pcm")
    }

    private val mediaProjectionManager by lazy {
        getSystemService<MediaProjectionManager>()
    }

    private val mediaProjectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            super.onStop()
            Log.d(TAG, "MediaProjection stopped")
            releaseResources()
            stopService()
            saveToGallery()
        }
    }

    private fun saveToGallery() {
        serviceScope.launch {
            try {
                val timestamp = System.currentTimeMillis()
                
                // Save video file
                saveVideoToGallery(timestamp)
                
                // Save audio file if it exists
                if (audioOutputFile.exists() && audioOutputFile.length() > 0) {
                    saveAudioToGallery(timestamp)
                    Log.d(TAG, "Both video and audio files saved")
                } else {
                    Log.d(TAG, "Only video file saved (no audio recorded)")
                }
                
                // Clean up temporary files
                cleanupTempFiles()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error saving files to gallery", e)
            }
        }
    }
    
    private suspend fun saveVideoToGallery(timestamp: Long) {
        try {
            val videoValues = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, "screen_video_$timestamp.mp4")
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/ScreenRecordings")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }
            
            val videoCollection = if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }

            contentResolver.insert(videoCollection, videoValues)?.let { uri ->
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    FileInputStream(videoOutputFile).use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                
                // Mark as not pending
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    videoValues.clear()
                    videoValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                    contentResolver.update(uri, videoValues, null, null)
                }
                
                Log.d(TAG, "Video saved to gallery successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving video to gallery", e)
        }
    }
    
    private suspend fun saveAudioToGallery(timestamp: Long) {
        try {
            // Convert PCM to WAV first
            val wavFile = File(cacheDir, "screen_audio_$timestamp.wav")
            val conversionSuccess = AudioConverter.convertPcmToWav(audioOutputFile, wavFile)
            
            if (!conversionSuccess) {
                Log.e(TAG, "Failed to convert PCM to WAV")
                return
            }

            val audioValues = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, "screen_audio_$timestamp.wav")
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/ScreenRecordings")
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }
            }
            
            val audioCollection = if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }

            contentResolver.insert(audioCollection, audioValues)?.let { uri ->
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    FileInputStream(wavFile).use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                
                // Mark as not pending
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    audioValues.clear()
                    audioValues.put(MediaStore.Audio.Media.IS_PENDING, 0)
                    contentResolver.update(uri, audioValues, null, null)
                }
                
                Log.d(TAG, "Audio saved to gallery successfully as WAV")
                Log.d(TAG, AudioConverter.getAudioFileInfo(wavFile))
            }
            
            // Clean up the temporary WAV file
            if (wavFile.exists()) {
                wavFile.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving audio to gallery", e)
        }
    }
    
    private fun cleanupTempFiles() {
        try {
            if (videoOutputFile.exists()) {
                videoOutputFile.delete()
                Log.d(TAG, "Temporary video file deleted")
            }
            if (audioOutputFile.exists()) {
                audioOutputFile.delete()
                Log.d(TAG, "Temporary audio file deleted")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up temporary files", e)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when(intent?.action) {
            START_RECORDING -> {
                Log.d(TAG, "Starting screen recording service")
                val notification = NotificationHelper.createNotification(applicationContext)
                NotificationHelper.createNotificationChannel(applicationContext)
                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                _isServiceRunning.value = true
                startRecording(intent)
            }
            STOP_RECORDING -> {
                Log.d(TAG, "Stopping screen recording service")
                stopRecording()
            }
        }
        return START_STICKY
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun startRecording(intent: Intent) {
        try {
            val config = if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(
                    KEY_RECORDING_CONFIG,
                    ScreenRecordConfig::class.java
                )
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(KEY_RECORDING_CONFIG)
            }
            
            if(config == null) {
                Log.e(TAG, "Recording config is null")
                stopService()
                return
            }

            mediaProjection = config.data?.let {
                mediaProjectionManager?.getMediaProjection(config.resultCode, it)
            }
            
            if (mediaProjection == null) {
                Log.e(TAG, "Failed to create MediaProjection")
                stopService()
                return
            }
            
            mediaProjection?.registerCallback(mediaProjectionCallback, null)

            // Start audio recording first (if permission available)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && hasAudioPermission()) {
                startAudioRecording()
            } else {
                Log.w(TAG, "Audio permission not granted or Android version < 10, recording video only")
            }

            // Start video recording
            if (!initializeVideoRecorder()) {
                Log.e(TAG, "Failed to initialize video recorder")
                stopService()
                return
            }
            
            mediaRecorder.start()
            Log.d(TAG, "Video recording started successfully")

            virtualDisplay = createVirtualDisplay()
            if (virtualDisplay == null) {
                Log.e(TAG, "Failed to create virtual display")
                stopRecording()
                return
            }
            
            Log.d(TAG, "Screen recording started successfully ${if (isAudioRecording) "with" else "without"} system audio")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting recording", e)
            stopRecording()
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun startAudioRecording() {
        try {
            if (mediaProjection == null) {
                Log.e(TAG, "MediaProjection is null, cannot start audio recording")
                return
            }

            // Create AudioPlaybackCaptureConfiguration for system audio
            val audioPlaybackCaptureConfig = AudioPlaybackCaptureConfiguration.Builder(mediaProjection!!)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .addMatchingUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build()

            val audioFormat = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(AUDIO_SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                .build()

            val bufferSize = AudioRecord.getMinBufferSize(
                AUDIO_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            audioRecord = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    AudioRecord.Builder()
                        .setAudioFormat(audioFormat)
                        .setBufferSizeInBytes(bufferSize * 2)
                        .setAudioPlaybackCaptureConfig(audioPlaybackCaptureConfig)
                        .build()
                } catch (securityException: SecurityException) {
                    Log.e(TAG, "SecurityException creating AudioRecord: ${securityException.message}")
                    null
                }
            } else {
                null
            }

            audioRecord?.let { record ->
                record.startRecording()
                isAudioRecording = true
                
                // Start recording audio data in background
                audioRecordingJob = serviceScope.launch {
                    recordAudioData(record)
                }
                
                Log.d(TAG, "System audio recording started successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start audio recording", e)
            audioRecord = null
            isAudioRecording = false
        }
    }

    private suspend fun recordAudioData(audioRecord: AudioRecord) {
        val bufferSize = AudioRecord.getMinBufferSize(
            AUDIO_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val buffer = ByteArray(bufferSize)
        
        try {
            FileOutputStream(audioOutputFile).use { outputStream ->
                while (isActive && isAudioRecording) {
                    val bytesRead = audioRecord.read(buffer, 0, buffer.size)
                    if (bytesRead > 0) {
                        outputStream.write(buffer, 0, bytesRead)
                    } else if (bytesRead == AudioRecord.ERROR_INVALID_OPERATION) {
                        Log.e(TAG, "Audio recording error: Invalid operation")
                        break
                    }
                }
            }
            Log.d(TAG, "Audio recording completed, saved to: ${audioOutputFile.absolutePath}")
        } catch (e: IOException) {
            Log.e(TAG, "Error writing audio data", e)
        }
    }

    private fun stopRecording() {
        try {
            Log.d(TAG, "Stopping recording...")
            
            // Stop video recording
            mediaRecorder.stop()
            mediaRecorder.reset()
            
            // Stop audio recording
            stopAudioRecording()
            
            // Stop media projection
            mediaProjection?.stop()
            
            Log.d(TAG, "Recording stopped successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
            // Still try to clean up
            releaseResources()
            stopService()
        }
    }
    
    private fun stopAudioRecording() {
        try {
            isAudioRecording = false
            audioRecordingJob?.cancel()
            
            audioRecord?.let { record ->
                try {
                    record.stop()
                    Log.d(TAG, "Audio recording stopped")
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping audio recording", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in stopAudioRecording", e)
        }
    }

    private fun stopService() {
        _isServiceRunning.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.d(TAG, "Service stopped")
    }

    private fun hasAudioPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun getWindowSize(): Pair<Int, Int> {
        val calculator = WindowMetricsCalculator.getOrCreate()
        val metrics = calculator.computeMaximumWindowMetrics(applicationContext)
        return metrics.bounds.width() to metrics.bounds.height()
    }

    private fun getScaledDimensions(
        maxWidth: Int,
        maxHeight: Int,
        scaleFactor: Float = 0.8f
    ): Pair<Int, Int> {
        val aspectRatio = maxWidth / maxHeight.toFloat()

        var newWidth = (maxWidth * scaleFactor).toInt()
        var newHeight = (newWidth / aspectRatio).toInt()

        if(newHeight > (maxHeight * scaleFactor)) {
            newHeight = (maxHeight * scaleFactor).toInt()
            newWidth = (newHeight * aspectRatio).toInt()
        }

        // Ensure dimensions are even numbers (required for some encoders)
        newWidth = newWidth and 0xfffffffe.toInt()
        newHeight = newHeight and 0xfffffffe.toInt()

        return newWidth to newHeight
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun initializeVideoRecorder(): Boolean {
        return try {
            val (width, height) = getWindowSize()
            val (scaledWidth, scaledHeight) = getScaledDimensions(
                maxWidth = width,
                maxHeight = height
            )

            Log.d(TAG, "Video recording dimensions: ${scaledWidth}x${scaledHeight}")

            with(mediaRecorder) {
                // Set video source only (no audio on MediaRecorder)
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                
                // Set output format (video only)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setOutputFile(videoOutputFile.absolutePath)

                // Configure video settings
                setVideoSize(scaledWidth, scaledHeight)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setVideoEncodingBitRate(VIDEO_BIT_RATE)
                setVideoFrameRate(VIDEO_FRAME_RATE)

                prepare()
                Log.d(TAG, "Video recorder prepared successfully")
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing video recorder", e)
            false
        }
    }

    private fun createVirtualDisplay(): VirtualDisplay? {
        return try {
            val (width, height) = getWindowSize()
            mediaProjection?.createVirtualDisplay(
                "ScreenRecording",
                width,
                height,
                resources.displayMetrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mediaRecorder.surface,
                null,
                null
            ).also {
                Log.d(TAG, "Virtual display created successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating virtual display", e)
            null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        _isServiceRunning.value = false
        serviceScope.coroutineContext.cancelChildren()
        releaseResources()
    }

    private fun releaseResources() {
        try {
            stopAudioRecording()
            mediaRecorder.release()
            audioRecord?.release()
            virtualDisplay?.release()
            mediaProjection?.unregisterCallback(mediaProjectionCallback)
            mediaProjection = null
            virtualDisplay = null
            audioRecord = null
            Log.d(TAG, "Resources released successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing resources", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    companion object {
        private const val TAG = "ScreenRecordService"
        
        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning = _isServiceRunning.asStateFlow()

        private const val NOTIFICATION_ID = 1
        private const val VIDEO_FRAME_RATE = 30
        private const val VIDEO_BIT_RATE = 8_000_000 // 8 Mbps

        // Audio configuration constants
        private const val AUDIO_SAMPLE_RATE = 44100 // 44.1 kHz

        const val START_RECORDING = "START_RECORDING"
        const val STOP_RECORDING = "STOP_RECORDING"
        const val KEY_RECORDING_CONFIG = "KEY_RECORDING_CONFIG"
    }
}