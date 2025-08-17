package com.example.shortcutdemo

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import java.io.FileOutputStream
import java.io.IOException

object FrameSaver {
    private const val TAG = "FrameSaver"
    
    fun saveFramesToGallery(
        context: Context,
        frames: List<Bitmap>,
        chunkNumber: Int,
        timestamp: Long = System.currentTimeMillis()
    ): Int {
        var savedCount = 0
        
        try {
            Log.d(TAG, "Saving ${frames.size} unique frames for chunk $chunkNumber")
            
            frames.forEachIndexed { index, bitmap ->
                try {
                    val fileName = "unique_frame_${timestamp}_chunk${chunkNumber}_${index + 1}.jpg"
                    
                    if (saveFrameToGallery(context, bitmap, fileName)) {
                        savedCount++
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error saving frame $index for chunk $chunkNumber", e)
                }
            }
            
            Log.d(TAG, "Successfully saved $savedCount/${frames.size} frames for chunk $chunkNumber")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error saving frames for chunk $chunkNumber", e)
        }
        
        return savedCount
    }
    
    private fun saveFrameToGallery(context: Context, bitmap: Bitmap, fileName: String): Boolean {
        return try {
            val imageCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            
            val imageDetails = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ScreenRecordings/UniqueFrames")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            
            val imageUri = context.contentResolver.insert(imageCollection, imageDetails)
            
            if (imageUri != null) {
                context.contentResolver.openOutputStream(imageUri)?.use { outputStream ->
                    val success = bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
                    if (!success) {
                        Log.e(TAG, "Failed to compress bitmap for $fileName")
                        return false
                    }
                }
                
                // Mark as not pending
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    imageDetails.clear()
                    imageDetails.put(MediaStore.Images.Media.IS_PENDING, 0)
                    context.contentResolver.update(imageUri, imageDetails, null, null)
                }
                
                Log.d(TAG, "Frame saved successfully: $fileName")
                true
            } else {
                Log.e(TAG, "Failed to create MediaStore entry for $fileName")
                false
            }
            
        } catch (e: IOException) {
            Log.e(TAG, "IOException saving frame $fileName", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Exception saving frame $fileName", e)
            false
        }
    }
    
    fun getFrameSizeEstimate(frames: List<Bitmap>): String {
        if (frames.isEmpty()) return "0 KB"
        
        // Estimate size based on bitmap dimensions
        val avgWidth = frames.map { it.width }.average()
        val avgHeight = frames.map { it.height }.average()
        val avgSizeKB = (avgWidth * avgHeight * 3 / 1024).toInt() // Rough estimate for JPEG
        val totalSizeKB = avgSizeKB * frames.size
        
        return when {
            totalSizeKB < 1024 -> "${totalSizeKB} KB"
            totalSizeKB < 1024 * 1024 -> "${totalSizeKB / 1024} MB"
            else -> "${totalSizeKB / (1024 * 1024)} GB"
        }
    }
}