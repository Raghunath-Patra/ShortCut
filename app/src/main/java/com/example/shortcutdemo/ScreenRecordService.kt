package com.example.shortcutdemo

import android.Manifest
import android.app.Service
import android.content.ContentValues
import android.content.Context
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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
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
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class ScreenRecordService: Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var audioRecord: AudioRecord? = null
    private var audioRecordingJob: Job? = null
    
    // Interval recording variables
    private val intervalHandler = Handler(Looper.getMainLooper())
    private var intervalRunnable: Runnable? = null
    private val recordingSegmentNumber = AtomicInteger(0)
    private var savedMediaProjectionConfig: ScreenRecordConfig? = null
    private val isIntervalRecording = AtomicBoolean(false)
    
    // Use AtomicBoolean for thread-safe audio recording state
    private val isAudioRecording = AtomicBoolean(false)
    
    private val mediaRecorder by lazy {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(applicationContext)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
    }
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Current recording files (will change for each interval)
    private var currentVideoFile: File? = null
    private var currentAudioFile: File? = null

    private val mediaProjectionManager by lazy {
        getSystemService<MediaProjectionManager>()
    }

    private val mediaProjectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            super.onStop()
            Log.d(TAG, "MediaProjection stopped")
            
            // Only stop everything if user manually stopped AND we're not in interval recording mode
            if (!isIntervalRecording.get()) {
                releaseResources()
                stopService()
                saveCurrentRecordingToGallery()
            } else {
                Log.d(TAG, "MediaProjection stopped during interval transition - this is expected")
            }
        }
    }

    private fun savePreviousSegmentToGallery(videoFile: File, audioFile: File?, segmentNumber: Int) {
        try {
            val timestamp = System.currentTimeMillis()
            
            // Save video file
            if (videoFile.exists() && videoFile.length() > 0) {
                saveVideoToGallery(videoFile, timestamp, segmentNumber)
                Log.d(TAG, "Previous video segment $segmentNumber saved to gallery")
            } else {
                Log.w(TAG, "Previous video file is empty or doesn't exist for segment $segmentNumber")
            }
            
            // Save audio file if it exists and has content
            if (audioFile?.exists() == true && audioFile.length() > 0) {
                saveAudioToGallery(audioFile, timestamp, segmentNumber)
                Log.d(TAG, "Previous audio segment $segmentNumber saved to gallery")
            } else {
                Log.d(TAG, "No previous audio file to save for segment $segmentNumber")
            }
            
            // Clean up the saved files
            cleanupSegmentFiles(videoFile, audioFile)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error saving previous segment files to gallery", e)
        }
    }

    private fun saveCurrentRecordingToGallery() {
        val videoFile = currentVideoFile
        val audioFile = currentAudioFile
        
        if (videoFile == null) {
            Log.w(TAG, "No video file to save")
            return
        }
        
        serviceScope.launch {
            try {
                val timestamp = System.currentTimeMillis()
                val segmentNum = recordingSegmentNumber.get()
                
                // Save video file
                if (videoFile.exists() && videoFile.length() > 0) {
                    saveVideoToGallery(videoFile, timestamp, segmentNum)
                    Log.d(TAG, "Video segment $segmentNum saved to gallery")
                } else {
                    Log.w(TAG, "Video file is empty or doesn't exist for segment $segmentNum")
                }
                
                // Save audio file if it exists and has content
                if (audioFile?.exists() == true && audioFile.length() > 0) {
                    saveAudioToGallery(audioFile, timestamp, segmentNum)
                    Log.d(TAG, "Audio segment $segmentNum saved to gallery")
                } else {
                    Log.d(TAG, "No audio file to save for segment $segmentNum")
                }
                
                // Clean up the saved files
                cleanupSegmentFiles(videoFile, audioFile)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error saving segment files to gallery", e)
            }
        }
    }
    
    private fun saveVideoToGallery(videoFile: File, timestamp: Long, segmentNumber: Int) {
        try {
            val videoValues = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, "screen_video_${timestamp}_seg${segmentNumber}.mp4")
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
                    FileInputStream(videoFile).use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                
                // Mark as not pending
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    videoValues.clear()
                    videoValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                    contentResolver.update(uri, videoValues, null, null)
                }
                
                Log.d(TAG, "Video segment $segmentNumber saved to gallery successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving video segment $segmentNumber to gallery", e)
        }
    }
    
    private fun saveAudioToGallery(audioFile: File, timestamp: Long, segmentNumber: Int) {
        try {
            // Convert PCM to WAV first
            val wavFile = File(cacheDir, "screen_audio_${timestamp}_seg${segmentNumber}.wav")
            val conversionSuccess = AudioConverter.convertPcmToWav(audioFile, wavFile)
            
            if (!conversionSuccess) {
                Log.e(TAG, "Failed to convert PCM to WAV for segment $segmentNumber")
                return
            }

            val audioValues = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, "screen_audio_${timestamp}_seg${segmentNumber}.wav")
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
                
                Log.d(TAG, "Audio segment $segmentNumber saved to gallery successfully as WAV")
            }
            
            // Clean up the temporary WAV file
            if (wavFile.exists()) {
                wavFile.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving audio segment $segmentNumber to gallery", e)
        }
    }
    
    private fun cleanupSegmentFiles(videoFile: File?, audioFile: File?) {
        try {
            videoFile?.let {
                if (it.exists()) {
                    val deleted = it.delete()
                    Log.d(TAG, "Segment video file deleted: $deleted")
                }
            }
            audioFile?.let {
                if (it.exists()) {
                    val deleted = it.delete()
                    Log.d(TAG, "Segment audio file deleted: $deleted")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up segment files", e)
        }
    }

    private fun createNewSegmentFiles(): Pair<File, File> {
        val segmentNum = recordingSegmentNumber.incrementAndGet()
        val timestamp = System.currentTimeMillis()
        
        val videoFile = File(cacheDir, "screen_video_${timestamp}_seg${segmentNum}.mp4")
        val audioFile = File(cacheDir, "screen_audio_${timestamp}_seg${segmentNum}.pcm")
        
        return Pair(videoFile, audioFile)
    }

    private fun setupIntervalRecording() {
        cancelIntervalRecording() // Cancel any existing interval
        
        intervalRunnable = Runnable {
            if (isIntervalRecording.get()) {
                Log.d(TAG, "Starting next 30s recording segment")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startNextSegment()
                }
                setupIntervalRecording() // Schedule next interval
            }
        }
        
        intervalHandler.postDelayed(intervalRunnable!!, RECORDING_INTERVAL_MS)
        Log.d(TAG, "Scheduled next recording segment in ${RECORDING_INTERVAL_MS}ms")
    }

    private fun cancelIntervalRecording() {
        intervalRunnable?.let {
            intervalHandler.removeCallbacks(it)
        }
        intervalRunnable = null
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun startNextSegment() {
        try {
            Log.d(TAG, "Transitioning to next recording segment...")
            
            // Keep references to current files for background saving
            val previousVideoFile = currentVideoFile
            val previousAudioFile = currentAudioFile
            val previousSegmentNumber = recordingSegmentNumber.get()
            
            // 1. Stop the current recording and release the old virtual display.
            //    This is crucial to free up the resources for the next segment.
            stopCurrentRecording()
            
            // 2. Create new file paths for the upcoming segment.
            val (newVideoFile, newAudioFile) = createNewSegmentFiles()
            currentVideoFile = newVideoFile
            currentAudioFile = newAudioFile
            
            // 3. IMPORTANT: Do NOT create a new MediaProjection.
            //    We will reuse the existing one created when the service started.
            if (mediaProjection == null) {
                Log.e(TAG, "MediaProjection is null, cannot start next segment.")
                stopIntervalRecording()
                return
            }

            // 4. Start the next recording segment using the new files and the
            //    original, still-valid MediaProjection.
            startRecordingSegment()
            
            // 5. In the background, save the completed segment to the gallery.
            if (previousVideoFile != null) {
                serviceScope.launch {
                    savePreviousSegmentToGallery(previousVideoFile, previousAudioFile, previousSegmentNumber)
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error transitioning to next segment", e)
            stopIntervalRecording()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun startRecordingSegment() {
        try {
            // Initialize video recorder with new file
            if (!initializeVideoRecorder()) {
                Log.e(TAG, "Failed to initialize video recorder for new segment")
                stopIntervalRecording()
                return
            }

            // Start audio recording if permission available (Android 10+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && hasAudioPermission()) {
                startAudioRecording()
                // Small delay to ensure audio recording starts properly
                Thread.sleep(50)
            }

            // Start video recording
            mediaRecorder.start()
            Log.d(TAG, "Video recording started for segment ${recordingSegmentNumber.get()}")

            virtualDisplay = createVirtualDisplay()
            if (virtualDisplay == null) {
                Log.e(TAG, "Failed to create virtual display for new segment")
                stopIntervalRecording()
                return
            }
            
            // Update notification to show current segment
            val updatedNotification = NotificationHelper.updateNotificationWithSegment(
                applicationContext, 
                recordingSegmentNumber.get()
            )
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.notify(NOTIFICATION_ID, updatedNotification)
            
            Log.d(TAG, "Recording segment ${recordingSegmentNumber.get()} started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting recording segment", e)
            stopIntervalRecording()
        }
    }

    private fun stopCurrentRecording() {
        try {
            // Stop audio recording
            stopAudioRecording()
            
            // Stop video recording
            try {
                mediaRecorder.stop()
                Log.d(TAG, "Video recording stopped for segment transition")
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping video recording for segment", e)
            }
            
            mediaRecorder.reset()
            
            // Release virtual display but keep MediaProjection
            try {
                virtualDisplay?.release()
                virtualDisplay = null
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing virtual display", e)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping current recording segment", e)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when(intent?.action) {
            START_RECORDING -> {
                Log.d(TAG, "Starting screen recording service with intervals")
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
                startIntervalRecording(intent)
            }
            STOP_RECORDING -> {
                Log.d(TAG, "Stopping interval screen recording service")
                stopIntervalRecording()
            }
        }
        return START_STICKY
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun startIntervalRecording(intent: Intent) {
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

            // Save the config for reuse in subsequent segments
            savedMediaProjectionConfig = config

            mediaProjection = config.data?.let {
                mediaProjectionManager?.getMediaProjection(config.resultCode, it)
            }
            
            if (mediaProjection == null) {
                Log.e(TAG, "Failed to create MediaProjection")
                stopService()
                return
            }
            
            mediaProjection?.registerCallback(mediaProjectionCallback, null)

            // Start interval recording
            isIntervalRecording.set(true)
            recordingSegmentNumber.set(0)
            
            // Create initial files
            val (videoFile, audioFile) = createNewSegmentFiles()
            currentVideoFile = videoFile
            currentAudioFile = audioFile
            
            // Start first recording segment
            startRecordingSegment()
            
            // Setup interval timer for subsequent segments
            setupIntervalRecording()
            
            Log.d(TAG, "Interval screen recording started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting interval recording", e)
            stopIntervalRecording()
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun startAudioRecording() {
        try {
            if (mediaProjection == null) {
                Log.e(TAG, "MediaProjection is null, cannot start audio recording")
                return
            }

            // Release previous AudioRecord if it exists
            releaseAudioRecord()

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
                isAudioRecording.set(true)
                
                // Start recording audio data in background
                audioRecordingJob = serviceScope.launch {
                    recordAudioData(record)
                }
                
                Log.d(TAG, "System audio recording started for segment ${recordingSegmentNumber.get()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start audio recording", e)
            audioRecord = null
            isAudioRecording.set(false)
        }
    }

    private suspend fun recordAudioData(audioRecord: AudioRecord) {
        val bufferSize = AudioRecord.getMinBufferSize(
            AUDIO_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val buffer = ByteArray(bufferSize)
        
        val audioFile = currentAudioFile
        if (audioFile == null) {
            Log.e(TAG, "No audio file available for recording")
            return
        }
        
        try {
            FileOutputStream(audioFile).use { outputStream ->
                // Use the AtomicBoolean and check if the coroutine job is active
                while (isAudioRecording.get() && audioRecordingJob?.isActive == true) {
                    val bytesRead = audioRecord.read(buffer, 0, buffer.size)
                    if (bytesRead > 0) {
                        outputStream.write(buffer, 0, bytesRead)
                    } else if (bytesRead == AudioRecord.ERROR_INVALID_OPERATION) {
                        Log.e(TAG, "Audio recording error: Invalid operation")
                        break
                    } else if (bytesRead == AudioRecord.ERROR_BAD_VALUE) {
                        Log.e(TAG, "Audio recording error: Bad value")
                        break
                    }
                }
            }
            Log.d(TAG, "Audio recording completed for segment, saved to: ${audioFile.absolutePath}, size: ${audioFile.length()} bytes")
        } catch (e: IOException) {
            Log.e(TAG, "Error writing audio data", e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error in audio recording", e)
        }
    }

    private fun stopIntervalRecording() {
        try {
            Log.d(TAG, "Stopping interval recording...")
            
            isIntervalRecording.set(false)
            cancelIntervalRecording()
            
            // Save current segment
            saveCurrentRecordingToGallery()
            
            // Stop current recording
            stopCurrentRecording()
            
            // Stop media projection
            mediaProjection?.stop()
            
            Log.d(TAG, "Interval recording stopped successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping interval recording", e)
        } finally {
            releaseResources()
            stopService()
        }
    }
    
    private fun stopAudioRecording() {
        try {
            isAudioRecording.set(false)
            
            // Cancel the audio recording job immediately
            audioRecordingJob?.cancel()
            audioRecordingJob = null
            
            audioRecord?.let { record ->
                try {
                    if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        record.stop()
                    }
                    // Don't release here during transitions, only release during full stop
                    Log.d(TAG, "Audio recording stopped")
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping audio recording", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in stopAudioRecording", e)
        }
    }

    private fun releaseAudioRecord() {
        try {
            audioRecord?.let { record ->
                try {
                    record.release()
                    audioRecord = null
                    Log.d(TAG, "Audio recording released")
                } catch (e: Exception) {
                    Log.e(TAG, "Error releasing audio recording", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in releaseAudioRecord", e)
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
            val videoFile = currentVideoFile
            if (videoFile == null) {
                Log.e(TAG, "No video file available for recording")
                return false
            }
            
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
                setOutputFile(videoFile.absolutePath)

                // Configure video settings
                setVideoSize(scaledWidth, scaledHeight)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setVideoEncodingBitRate(VIDEO_BIT_RATE)
                setVideoFrameRate(VIDEO_FRAME_RATE)

                prepare()
                Log.d(TAG, "Video recorder prepared successfully for segment ${recordingSegmentNumber.get()}")
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
                Log.d(TAG, "Virtual display created successfully for segment ${recordingSegmentNumber.get()}")
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
        isIntervalRecording.set(false)
        cancelIntervalRecording()
        serviceScope.coroutineContext.cancelChildren()
        releaseResources()
    }

    private fun releaseResources() {
        try {
            stopAudioRecording()
            releaseAudioRecord()
            
            try {
                mediaRecorder.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing MediaRecorder", e)
            }
            
            try {
                virtualDisplay?.release()
                virtualDisplay = null
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing VirtualDisplay", e)
            }
            
            try {
                mediaProjection?.unregisterCallback(mediaProjectionCallback)
                mediaProjection = null
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing MediaProjection", e)
            }
            
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
        
        // Interval recording constant
        private const val RECORDING_INTERVAL_MS = 30_000L // 30 seconds

        const val START_RECORDING = "START_RECORDING"
        const val STOP_RECORDING = "STOP_RECORDING"
        const val KEY_RECORDING_CONFIG = "KEY_RECORDING_CONFIG"
    }
}