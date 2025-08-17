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
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.Surface
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
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class ScreenRecordService: Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var audioRecord: AudioRecord? = null
    private var audioRecordingJob: Job? = null
    
    // Video encoding with MediaCodec and MediaMuxer for chunking
    private var videoEncoder: MediaCodec? = null
    private var currentMuxer: MediaMuxer? = null
    private var videoTrackIndex = -1
    private var muxerStarted = false
    private var inputSurface: Surface? = null
    private val muxerLock = Any() // Synchronization lock for muxer operations
    private var cachedVideoFormat: MediaFormat? = null // Cache the video format for subsequent chunks
    
    // Chunking variables
    private val chunkHandler = Handler(Looper.getMainLooper())
    private var chunkRunnable: Runnable? = null
    private val chunkNumber = AtomicInteger(0)
    private val isRecording = AtomicBoolean(false)
    private val chunkStartTime = AtomicLong(0)
    
    // Audio recording state
    private val isAudioRecording = AtomicBoolean(false)
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Current recording files - will be chunked every 30 seconds
    private var currentVideoFile: File? = null
    private var currentAudioFile: File? = null
    private var audioOutputStream: FileOutputStream? = null

    private val mediaProjectionManager by lazy {
        getSystemService<MediaProjectionManager>()
    }

    private val mediaProjectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            super.onStop()
            Log.d(TAG, "MediaProjection stopped by user")
            stopRecording()
        }
    }

    private fun createChunkFiles(): Pair<File, File> {
        val chunkNum = chunkNumber.get()
        val timestamp = System.currentTimeMillis()
        
        val videoFile = File(cacheDir, "video_chunk_${timestamp}_${chunkNum}.mp4")
        val audioFile = File(cacheDir, "audio_chunk_${timestamp}_${chunkNum}.pcm")
        
        return Pair(videoFile, audioFile)
    }

    private fun setupChunkTimer() {
        cancelChunkTimer()
        
        chunkRunnable = Runnable {
            if (isRecording.get()) {
                Log.d(TAG, "Processing 30s chunk ${chunkNumber.get()}")
                serviceScope.launch {
                    processCurrentChunk()
                }
                setupChunkTimer() // Schedule next chunk
            }
        }
        
        chunkHandler.postDelayed(chunkRunnable!!, CHUNK_INTERVAL_MS)
        Log.d(TAG, "Scheduled next chunk processing in ${CHUNK_INTERVAL_MS}ms")
    }

    private fun cancelChunkTimer() {
        chunkRunnable?.let {
            chunkHandler.removeCallbacks(it)
        }
        chunkRunnable = null
    }

    private suspend fun processCurrentChunk() {
        try {
            val currentChunkNum = chunkNumber.get()
            Log.d(TAG, "Starting to process chunk $currentChunkNum")
            
            // Get current audio data first (before finalizing video)
            val audioChunk = getCurrentAudioChunk()
            
            // Wait a bit longer to ensure the muxer has been properly started
            // and has received some frames before trying to stop it
            kotlinx.coroutines.delay(200)
            
            // Finalize current video chunk
            val videoChunk = finalizeCurrentVideoChunk()
            
            // Only process if we actually have video content
            if (videoChunk != null && videoChunk.exists() && videoChunk.length() > 0) {
                // Process chunks in background
                processChunks(videoChunk, audioChunk, currentChunkNum)
            } else {
                Log.w(TAG, "No valid video chunk for chunk $currentChunkNum, only processing audio")
                // Still process audio if we have it
                if (audioChunk != null && audioChunk.isNotEmpty()) {
                    processChunks(null, audioChunk, currentChunkNum)
                }
            }
            
            // Increment chunk number
            chunkNumber.incrementAndGet()
            
            // Start next video chunk
            startNextVideoChunk()
            
            // Reset audio for next chunk
            resetAudioForNextChunk()
            
            Log.d(TAG, "Chunk $currentChunkNum processed, continuing recording...")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing chunk", e)
        }
    }

    private fun finalizeCurrentVideoChunk(): File? {
        return try {
            val videoFile = currentVideoFile
            
            synchronized(muxerLock) {
                currentMuxer?.let { muxer ->
                    try {
                        // Check if muxer has been properly started and has received data
                        if (muxerStarted && videoTrackIndex >= 0) {
                            Log.d(TAG, "Stopping muxer for chunk ${chunkNumber.get()}, track: $videoTrackIndex")
                            
                            // Signal that muxer is stopping to prevent new writes
                            muxerStarted = false
                            
                            // Wait for any pending writes to complete
                            Thread.sleep(150)
                            
                            // Check if the file has any content before stopping
                            val hasContent = videoFile?.exists() == true && videoFile.length() > 1024 // At least 1KB
                            
                            if (hasContent) {
                                muxer.stop()
                                Log.d(TAG, "Muxer stopped successfully for chunk ${chunkNumber.get()}")
                            } else {
                                Log.w(TAG, "Video file has no content, skipping muxer stop for chunk ${chunkNumber.get()}")
                            }
                        } else {
                            Log.w(TAG, "Skipping muxer stop - not properly initialized. Started: $muxerStarted, TrackIndex: $videoTrackIndex")
                            muxerStarted = false
                        }
                        
                        // Always release the muxer
                        muxer.release()
                        Log.d(TAG, "Muxer released for chunk ${chunkNumber.get()}")
                        
                    } catch (e: Exception) {
                        Log.e(TAG, "Error during muxer finalization for chunk ${chunkNumber.get()}", e)
                        // Try to release anyway
                        try {
                            muxer.release()
                        } catch (releaseError: Exception) {
                            Log.e(TAG, "Error releasing muxer during error handling", releaseError)
                        }
                    } finally {
                        // Always reset state
                        currentMuxer = null
                        videoTrackIndex = -1
                        muxerStarted = false
                    }
                }
            }
            
            val fileSize = videoFile?.length() ?: 0
            Log.d(TAG, "Video chunk finalized: ${videoFile?.name}, size: $fileSize bytes")
            
            // Only return the file if it has meaningful content (more than just headers)
            if (fileSize > 1024) videoFile else {
                Log.w(TAG, "Video chunk too small ($fileSize bytes), likely no actual video data")
                null
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error finalizing video chunk", e)
            null
        }
    }

    private fun startNextVideoChunk() {
        try {
            // Create new video file for next chunk
            val (newVideoFile, _) = createChunkFiles()
            currentVideoFile = newVideoFile
            
            synchronized(muxerLock) {
                // Small delay to ensure previous muxer is fully released
                Thread.sleep(100)
                
                // Initialize new muxer for next chunk
                if (initializeVideoMuxer(newVideoFile)) {
                    Log.d(TAG, "Next video chunk started: ${newVideoFile.name}")
                    
                    // If the muxer was immediately started with cached format, log it
                    if (muxerStarted) {
                        Log.d(TAG, "Muxer ready immediately for chunk ${chunkNumber.get()}")
                    } else {
                        Log.d(TAG, "Muxer waiting for frames for chunk ${chunkNumber.get()}")
                    }
                } else {
                    Log.e(TAG, "Failed to initialize muxer for next chunk")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting next video chunk", e)
        }
    }

    // Replace the processChunks method in ScreenRecordService.kt with this:

    private suspend fun processChunks(videoFile: File?, audioData: ByteArray?, chunkNumber: Int) {
        try {
            val timestamp = System.currentTimeMillis()
            
            // Process video chunk with frame extraction
            if (videoFile != null && videoFile.exists() && videoFile.length() > 0) {
                Log.d(TAG, "Processing video chunk $chunkNumber with frame extraction")
                
                // Check if OpenCV is ready, otherwise use simple extractor
                if (OpenCVInitializer.isOpenCVReady()) {
                    Log.d(TAG, "Using OpenCV-based frame extraction for chunk $chunkNumber")
                    // Use OpenCV-based extractor
                    val frameExtractor = HashBasedFrameExtractor()
                    val result = frameExtractor.extractUniqueFrames(
                        videoFile = videoFile,
                        hashThreshold = 5,
                        interval = 5,
                        maxFrames = 50
                    )
                    
                    Log.d(TAG, "OpenCV extraction results for chunk $chunkNumber:")
                    Log.d(TAG, "- Unique frames: ${result.uniqueFrames.size}")
                    Log.d(TAG, "- Processing time: ${result.processingTimeMs}ms")
                    
                    if (result.uniqueFrames.isNotEmpty()) {
                        val savedCount = FrameSaver.saveFramesToGallery(
                            context = applicationContext,
                            frames = result.uniqueFrames,
                            chunkNumber = chunkNumber,
                            timestamp = timestamp
                        )
                        Log.d(TAG, "Saved $savedCount OpenCV-extracted frames for chunk $chunkNumber")
                    }
                } else {
                    Log.d(TAG, "Using simple frame extraction for chunk $chunkNumber (OpenCV not available)")
                    // Fallback to simple extractor
                    val frameExtractor = SimpleFrameExtractor()
                    val result = frameExtractor.extractUniqueFrames(
                        videoFile = videoFile,
                        intervalSeconds = 2,
                        maxFrames = 50
                    )
                    
                    Log.d(TAG, "Simple extraction results for chunk $chunkNumber:")
                    Log.d(TAG, "- Unique frames: ${result.uniqueFrames.size}")
                    Log.d(TAG, "- Processing time: ${result.processingTimeMs}ms")
                    
                    if (result.uniqueFrames.isNotEmpty()) {
                        val savedCount = FrameSaver.saveFramesToGallery(
                            context = applicationContext,
                            frames = result.uniqueFrames,
                            chunkNumber = chunkNumber,
                            timestamp = timestamp
                        )
                        Log.d(TAG, "Saved $savedCount simple-extracted frames for chunk $chunkNumber")
                    }
                }
                
                // Clean up video file
                videoFile.delete()
                Log.d(TAG, "Video chunk $chunkNumber processed and cleaned up")
            } else {
                Log.w(TAG, "No valid video chunk to process for chunk $chunkNumber")
            }
            
            // Keep existing audio processing
            if (audioData != null && audioData.isNotEmpty()) {
                saveAudioChunkToGallery(audioData, timestamp, chunkNumber)
                Log.d(TAG, "Audio chunk $chunkNumber processed")
            } else {
                Log.w(TAG, "No audio data to save for chunk $chunkNumber")
            }
            
            Log.d(TAG, "Chunk $chunkNumber processing completed")
            
            // TODO: In production, replace gallery saving with backend API call
            // sendFramesToBackend(result.uniqueFrames, audioData, chunkNumber)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing chunks", e)
        }
    }

    private fun getCurrentAudioChunk(): ByteArray? {
        return try {
            val audioFile = currentAudioFile
            if (audioFile?.exists() == true && audioFile.length() > 0) {
                val data = audioFile.readBytes()
                Log.d(TAG, "Retrieved audio chunk: ${data.size} bytes")
                data
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting audio chunk", e)
            null
        }
    }

    private fun resetAudioForNextChunk() {
        try {
            // Close current audio file and create new one for next chunk
            audioOutputStream?.close()
            
            // Create new audio file for next chunk
            val (_, newAudioFile) = createChunkFiles()
            currentAudioFile = newAudioFile
            audioOutputStream = FileOutputStream(newAudioFile)
            
            Log.d(TAG, "Audio reset for next chunk: ${newAudioFile.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting audio for next chunk", e)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when(intent?.action) {
            START_RECORDING -> {
                Log.d(TAG, "Starting continuous chunked screen recording")
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
                startContinuousRecording(intent)
            }
            STOP_RECORDING -> {
                Log.d(TAG, "Stopping continuous screen recording")
                stopRecording()
            }
        }
        return START_STICKY
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun startContinuousRecording(intent: Intent) {
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

            // Initialize recording
            isRecording.set(true)
            chunkNumber.set(0)
            chunkStartTime.set(System.currentTimeMillis())
            
            // Create initial chunk files
            val (videoFile, audioFile) = createChunkFiles()
            currentVideoFile = videoFile
            currentAudioFile = audioFile
            
            // Initialize video recording with MediaCodec + MediaMuxer
            if (!initializeVideoEncoder()) {
                Log.e(TAG, "Failed to initialize video encoder")
                stopRecording()
                return
            }

            // Initialize video muxer
            if (!initializeVideoMuxer(videoFile)) {
                Log.e(TAG, "Failed to initialize video muxer")
                stopRecording()
                return
            }

            // Start audio recording if permission available (Android 10+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && hasAudioPermission()) {
                startContinuousAudioRecording()
            }

            // Start video encoding
            startVideoEncoding()

            // Create virtual display
            virtualDisplay = createVirtualDisplay()
            if (virtualDisplay == null) {
                Log.e(TAG, "Failed to create virtual display")
                stopRecording()
                return
            }
            
            // Setup chunk processing timer
            setupChunkTimer()
            
            Log.d(TAG, "Continuous chunked recording started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting continuous recording", e)
            stopRecording()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun initializeVideoEncoder(): Boolean {
        return try {
            val (width, height) = getWindowSize()
            val (scaledWidth, scaledHeight) = getScaledDimensions(
                maxWidth = width,
                maxHeight = height
            )

            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, scaledWidth, scaledHeight).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_BIT_RATE)
                setInteger(MediaFormat.KEY_FRAME_RATE, VIDEO_FRAME_RATE)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2) // I-frame every 2 seconds for better chunk boundaries
                
                // Add these for better chunking compatibility
                setInteger(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 1000000) // 1 second
                setInteger(MediaFormat.KEY_PREPEND_HEADER_TO_SYNC_FRAMES, 1) // Include SPS/PPS with keyframes
            }

            videoEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            videoEncoder?.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            inputSurface = videoEncoder?.createInputSurface()
            
            Log.d(TAG, "Video encoder initialized: ${scaledWidth}x${scaledHeight}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing video encoder", e)
            false
        }
    }

    private fun initializeVideoMuxer(outputFile: File): Boolean {
        return try {
            synchronized(muxerLock) {
                currentMuxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                muxerStarted = false
                videoTrackIndex = -1
                
                // If we have a cached format (from previous chunks), use it immediately
                cachedVideoFormat?.let { format ->
                    try {
                        videoTrackIndex = currentMuxer!!.addTrack(format)
                        currentMuxer!!.start()
                        muxerStarted = true
                        Log.d(TAG, "Muxer started immediately with cached format for: ${outputFile.name}, track: $videoTrackIndex")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error starting muxer with cached format", e)
                        videoTrackIndex = -1
                        muxerStarted = false
                    }
                }
                
                Log.d(TAG, "Video muxer initialized for: ${outputFile.name}")
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing video muxer", e)
            false
        }
    }

    private fun startVideoEncoding() {
        try {
            videoEncoder?.start()
            
            // Start encoding job
            serviceScope.launch {
                encodeVideoFrames()
            }
            
            Log.d(TAG, "Video encoding started")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting video encoding", e)
        }
    }

    private suspend fun encodeVideoFrames() {
        val bufferInfo = MediaCodec.BufferInfo()
        var configDataSent = false // Track if we've already sent codec config data
        
        try {
            while (isRecording.get()) {
                val encoder = videoEncoder ?: break
                
                val outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                
                when {
                    outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        synchronized(muxerLock) {
                            val format = encoder.outputFormat
                            
                            // Cache the format for future chunks
                            cachedVideoFormat = format
                            Log.d(TAG, "Video format cached for future chunks")
                            
                            val muxer = currentMuxer
                            if (muxer != null && videoTrackIndex < 0) {
                                try {
                                    videoTrackIndex = muxer.addTrack(format)
                                    muxer.start()
                                    muxerStarted = true
                                    configDataSent = false // Reset for new muxer
                                    Log.d(TAG, "Muxer started for chunk ${chunkNumber.get()}, track index: $videoTrackIndex")
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error starting muxer", e)
                                    videoTrackIndex = -1
                                    muxerStarted = false
                                }
                            }
                        }
                    }
                    outputBufferIndex >= 0 -> {
                        val outputBuffer = encoder.getOutputBuffer(outputBufferIndex)
                        if (outputBuffer != null && bufferInfo.size > 0) {
                            synchronized(muxerLock) {
                                val muxer = currentMuxer
                                if (muxer != null && muxerStarted && videoTrackIndex >= 0) {
                                    try {
                                        // Check if this is codec config data (SPS/PPS)
                                        val isConfigFrame = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                                        
                                        if (isConfigFrame) {
                                            if (!configDataSent) {
                                                // Only send config data once per muxer
                                                muxer.writeSampleData(videoTrackIndex, outputBuffer, bufferInfo)
                                                configDataSent = true
                                                Log.d(TAG, "Config data sent for chunk ${chunkNumber.get()}")
                                            } else {
                                                Log.d(TAG, "Skipping duplicate config data")
                                            }
                                        } else {
                                            // Regular frame data
                                            muxer.writeSampleData(videoTrackIndex, outputBuffer, bufferInfo)
                                        }
                                    } catch (e: IllegalStateException) {
                                        // Muxer was stopped during write, this is expected during chunk transitions
                                        Log.d(TAG, "Muxer write failed (chunk transition): ${e.message}")
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error writing sample data", e)
                                    }
                                } else if (muxer != null && !muxerStarted && videoTrackIndex < 0) {
                                    // If muxer exists but isn't started, and we have cached format, try to start it
                                    cachedVideoFormat?.let { format ->
                                        try {
                                            videoTrackIndex = muxer.addTrack(format)
                                            muxer.start()
                                            muxerStarted = true
                                            configDataSent = false
                                            Log.d(TAG, "Muxer started with cached format during encoding for chunk ${chunkNumber.get()}")
                                            
                                            // Now try to write the current frame
                                            val isConfigFrame = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                                            if (!isConfigFrame) {
                                                muxer.writeSampleData(videoTrackIndex, outputBuffer, bufferInfo)
                                            }
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Error starting muxer with cached format during encoding", e)
                                            videoTrackIndex = -1
                                            muxerStarted = false
                                        }
                                    }
                                }
                            }
                        }
                        encoder.releaseOutputBuffer(outputBufferIndex, false)
                    }
                    outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        // No output available yet, small delay
                        kotlinx.coroutines.delay(5)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in video encoding loop", e)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun startContinuousAudioRecording() {
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
                isAudioRecording.set(true)
                
                // Initialize audio output stream
                audioOutputStream = FileOutputStream(currentAudioFile!!)
                
                // Start recording audio data continuously
                audioRecordingJob = serviceScope.launch {
                    recordAudioDataContinuously(record)
                }
                
                Log.d(TAG, "Continuous audio recording started")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start audio recording", e)
            audioRecord = null
            isAudioRecording.set(false)
        }
    }

    private suspend fun recordAudioDataContinuously(audioRecord: AudioRecord) {
        val bufferSize = AudioRecord.getMinBufferSize(
            AUDIO_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val buffer = ByteArray(bufferSize)
        
        try {
            while (isAudioRecording.get() && audioRecordingJob?.isActive == true) {
                val bytesRead = audioRecord.read(buffer, 0, buffer.size)
                if (bytesRead > 0) {
                    // Write to current chunk file
                    audioOutputStream?.write(buffer, 0, bytesRead)
                    audioOutputStream?.flush()
                } else if (bytesRead == AudioRecord.ERROR_INVALID_OPERATION) {
                    Log.e(TAG, "Audio recording error: Invalid operation")
                    break
                } else if (bytesRead == AudioRecord.ERROR_BAD_VALUE) {
                    Log.e(TAG, "Audio recording error: Bad value")
                    break
                }
            }
            Log.d(TAG, "Audio recording completed")
        } catch (e: IOException) {
            Log.e(TAG, "Error writing audio data", e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error in audio recording", e)
        }
    }

    private fun saveVideoChunkToGallery(videoFile: File, timestamp: Long, chunkNumber: Int) {
        try {
            val videoValues = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, "video_chunk_${timestamp}_${chunkNumber}.mp4")
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
                
                Log.d(TAG, "Video chunk $chunkNumber saved to gallery successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving video chunk $chunkNumber to gallery", e)
        }
    }
    
    private fun saveAudioChunkToGallery(audioData: ByteArray, timestamp: Long, chunkNumber: Int) {
        try {
            // Convert PCM to WAV first
            val wavFile = File(cacheDir, "audio_chunk_${timestamp}_${chunkNumber}.wav")
            val tempPcmFile = File(cacheDir, "temp_audio_chunk_${timestamp}_${chunkNumber}.pcm")
            
            // Write PCM data to temp file
            tempPcmFile.writeBytes(audioData)
            
            val conversionSuccess = AudioConverter.convertPcmToWav(tempPcmFile, wavFile)
            
            if (!conversionSuccess) {
                Log.e(TAG, "Failed to convert PCM to WAV for chunk $chunkNumber")
                tempPcmFile.delete()
                return
            }

            val audioValues = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, "audio_chunk_${timestamp}_${chunkNumber}.wav")
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
                
                Log.d(TAG, "Audio chunk $chunkNumber saved to gallery successfully as WAV")
            }
            
            // Clean up temporary files
            tempPcmFile.delete()
            wavFile.delete()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error saving audio chunk $chunkNumber to gallery", e)
        }
    }

    private fun stopRecording() {
        try {
            Log.d(TAG, "Stopping continuous recording...")
            
            isRecording.set(false)
            cancelChunkTimer()
            
            // Process final chunk
            serviceScope.launch {
                processCurrentChunk()
            }
            
            // Stop audio recording
            stopAudioRecording()
            
            // Stop video recording
            stopVideoRecording()
            
            Log.d(TAG, "Recording stopped successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
        } finally {
            releaseResources()
            stopService()
        }
    }

    private fun stopVideoRecording() {
        try {
            // Stop video encoder
            videoEncoder?.let { encoder ->
                try {
                    encoder.signalEndOfInputStream()
                    encoder.stop()
                    encoder.release()
                    videoEncoder = null
                    Log.d(TAG, "Video encoder stopped")
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping video encoder", e)
                }
            }
            
            // Stop current muxer
            currentMuxer?.let { muxer ->
                try {
                    if (muxerStarted) {
                        muxer.stop()
                        muxerStarted = false
                    }
                    muxer.release()
                    currentMuxer = null
                    videoTrackIndex = -1
                    Log.d(TAG, "Video muxer stopped")
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping muxer", e)
                }
            }
            
            // Release input surface
            inputSurface?.release()
            inputSurface = null
            
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping video recording", e)
        }
    }
    
    private fun stopAudioRecording() {
        try {
            isAudioRecording.set(false)
            
            // Cancel the audio recording job
            audioRecordingJob?.cancel()
            audioRecordingJob = null
            
            // Close audio output stream
            audioOutputStream?.close()
            audioOutputStream = null
            
            audioRecord?.let { record ->
                try {
                    if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        record.stop()
                    }
                    record.release()
                    audioRecord = null
                    Log.d(TAG, "Audio recording stopped and released")
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

    private fun createVirtualDisplay(): VirtualDisplay? {
        return try {
            val (width, height) = getWindowSize()
            mediaProjection?.createVirtualDisplay(
                "ScreenRecording",
                width,
                height,
                resources.displayMetrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                inputSurface,
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
        isRecording.set(false)
        cancelChunkTimer()
        serviceScope.coroutineContext.cancelChildren()
        releaseResources()
    }

    private fun releaseResources() {
        try {
            stopAudioRecording()
            stopVideoRecording()
            
            // Clear cached format
            cachedVideoFormat = null
            
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
        private const val TIMEOUT_US = 10_000L // 10ms timeout for MediaCodec

        // Audio configuration constants
        private const val AUDIO_SAMPLE_RATE = 44100 // 44.1 kHz
        
        // Chunk interval constant (30 seconds)
        private const val CHUNK_INTERVAL_MS = 30_000L

        const val START_RECORDING = "START_RECORDING"
        const val STOP_RECORDING = "STOP_RECORDING"
        const val KEY_RECORDING_CONFIG = "KEY_RECORDING_CONFIG"
    }
}