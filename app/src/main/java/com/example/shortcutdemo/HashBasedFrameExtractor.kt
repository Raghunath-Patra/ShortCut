package com.example.shortcutdemo

import android.graphics.Bitmap
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.opencv.videoio.VideoCapture
import org.opencv.videoio.Videoio
import java.io.File
import kotlin.math.abs

class HashBasedFrameExtractor {
    
    companion object {
        private const val TAG = "HashFrameExtractor"
        private const val HASH_SIZE = 8 // 8x8 hash = 64 bits
    }
    
    data class ExtractionResult(
        val uniqueFrames: List<Bitmap>,
        val totalFramesProcessed: Int,
        val processingTimeMs: Long
    )
    
    fun extractUniqueFrames(
        videoFile: File,
        hashThreshold: Int = 5,
        interval: Int = 5,
        maxFrames: Int = 50
    ): ExtractionResult {
        
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "Starting hash-based frame extraction from: ${videoFile.name}")
        Log.d(TAG, "Parameters - Threshold: $hashThreshold, Interval: $interval, MaxFrames: $maxFrames")
        
        if (!videoFile.exists()) {
            Log.e(TAG, "Video file does not exist: ${videoFile.absolutePath}")
            return ExtractionResult(emptyList(), 0, 0)
        }
        
        val cap = VideoCapture(videoFile.absolutePath)
        if (!cap.isOpened) {
            Log.e(TAG, "Failed to open video file: ${videoFile.absolutePath}")
            return ExtractionResult(emptyList(), 0, 0)
        }
        
        // Get video properties
        val fps = cap.get(Videoio.CAP_PROP_FPS)
        val totalFrames = cap.get(Videoio.CAP_PROP_FRAME_COUNT).toInt()
        val duration = totalFrames / fps
        
        Log.d(TAG, "Video properties - FPS: $fps, Total frames: $totalFrames, Duration: ${duration}s")
        
        val uniqueFrames = mutableListOf<Bitmap>()
        val recentHashes = mutableListOf<String>()
        val mat = Mat()
        var frameCount = 0
        var processedCount = 0
        var uniqueCount = 0
        
        try {
            while (cap.read(mat) && uniqueCount < maxFrames) {
                frameCount++
                
                // Skip frames based on interval
                if (frameCount % interval != 0) {
                    continue
                }
                
                processedCount++
                
                try {
                    // Compute perceptual hash
                    val hash = computePerceptualHash(mat)
                    
                    // Check if frame is unique
                    if (isHashUnique(hash, recentHashes, hashThreshold)) {
                        // Convert to bitmap
                        val bitmap = matToBitmap(mat)
                        if (bitmap != null) {
                            uniqueFrames.add(bitmap)
                            recentHashes.add(hash)
                            uniqueCount++
                            
                            // Keep only recent hashes to save memory (last 100)
                            if (recentHashes.size > 100) {
                                recentHashes.removeAt(0)
                            }
                            
                            Log.d(TAG, "Unique frame $uniqueCount extracted at frame $frameCount")
                        }
                    }
                    
                    // Progress logging
                    if (processedCount % 50 == 0) {
                        Log.d(TAG, "Progress - Processed: $processedCount, Unique: $uniqueCount")
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing frame $frameCount", e)
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during frame extraction", e)
        } finally {
            cap.release()
        }
        
        val endTime = System.currentTimeMillis()
        val processingTime = endTime - startTime
        
        Log.d(TAG, "Extraction completed:")
        Log.d(TAG, "- Total frames in video: $totalFrames")
        Log.d(TAG, "- Frames processed: $processedCount")
        Log.d(TAG, "- Unique frames extracted: $uniqueCount")
        Log.d(TAG, "- Processing time: ${processingTime}ms")
        Log.d(TAG, "- Processing speed: ${processedCount.toFloat() / (processingTime / 1000f)} frames/sec")
        
        return ExtractionResult(uniqueFrames, processedCount, processingTime)
    }
    
    private fun computePerceptualHash(mat: Mat): String {
        val grayMat = Mat()
        val resizedMat = Mat()
        val dctMat = Mat()
        
        try {
            // Convert to grayscale if needed
            if (mat.channels() > 1) {
                Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_BGR2GRAY)
            } else {
                mat.copyTo(grayMat)
            }
            
            // Resize to HASH_SIZE x HASH_SIZE
            Imgproc.resize(grayMat, resizedMat, Size(HASH_SIZE.toDouble(), HASH_SIZE.toDouble()))
            
            // Convert to float for DCT
            resizedMat.convertTo(dctMat, CvType.CV_32F)
            
            // Simple hash using pixel intensity comparison (simplified perceptual hash)
            return computeSimpleHash(dctMat)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error computing hash", e)
            return ""
        } finally {
            grayMat.release()
            resizedMat.release()
            dctMat.release()
        }
    }
    
    private fun computeSimpleHash(mat: Mat): String {
        // Simple hash: compare each pixel with average
        val pixels = FloatArray((HASH_SIZE * HASH_SIZE))
        mat.get(0, 0, pixels)
        
        // Calculate average
        val average = pixels.average()
        
        // Create hash string
        val hashBits = StringBuilder()
        for (pixel in pixels) {
            hashBits.append(if (pixel > average) "1" else "0")
        }
        
        return hashBits.toString()
    }
    
    private fun isHashUnique(hash: String, recentHashes: List<String>, threshold: Int): Boolean {
        if (hash.isEmpty()) return false
        
        for (existingHash in recentHashes) {
            val distance = hammingDistance(hash, existingHash)
            if (distance <= threshold) {
                return false // Too similar to existing hash
            }
        }
        return true
    }
    
    private fun hammingDistance(hash1: String, hash2: String): Int {
        if (hash1.length != hash2.length) return Int.MAX_VALUE
        
        var distance = 0
        for (i in hash1.indices) {
            if (hash1[i] != hash2[i]) {
                distance++
            }
        }
        return distance
    }
    
    private fun matToBitmap(mat: Mat): Bitmap? {
        return try {
            val bitmap = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(mat, bitmap)
            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error converting Mat to Bitmap", e)
            null
        }
    }
}