package com.example.shortcutdemo

import android.util.Log

object OpenCVInitializer {
    private const val TAG = "OpenCVInitializer"
    private var isInitialized = false

    fun initializeOpenCV(callback: (Boolean) -> Unit) {
        if (isInitialized) {
            callback(true)
            return
        }

        try {
            // Load OpenCV native library directly (recommended approach)
            System.loadLibrary("opencv_java4")
            isInitialized = true
            Log.d(TAG, "OpenCV loaded successfully via System.loadLibrary")
            callback(true)
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "OpenCV native library not found", e)

            // Fallback: try alternative loading method
            try {
                if (org.opencv.android.OpenCVLoader.initDebug()) {
                    isInitialized = true
                    Log.d(TAG, "OpenCV loaded successfully via OpenCVLoader.initDebug")
                    callback(true)
                } else {
                    Log.e(TAG, "OpenCV initialization failed")
                    callback(false)
                }
            } catch (e2: Exception) {
                Log.e(TAG, "All OpenCV loading methods failed", e2)
                callback(false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during OpenCV initialization", e)
            callback(false)
        }
    }

    fun isOpenCVReady(): Boolean = isInitialized
}