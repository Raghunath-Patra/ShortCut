//package com.example.shortcutdemo
//
//import android.content.Context
//import android.media.MediaRecorder
//import android.os.Environment
//import android.util.Log
//import java.io.File
//import java.io.IOException
//import java.text.SimpleDateFormat
//import java.util.Date
//import java.util.Locale
//
//class ScreenRecorder(private val context: Context) {
//
//    private var mediaRecorder: MediaRecorder? = null
//
//    // This method starts the screen recording process
//    fun startRecording() {
//        try {
//            mediaRecorder = MediaRecorder()
//
//            // Setup media recorder
//            mediaRecorder?.apply {
//                setAudioSource(MediaRecorder.AudioSource.MIC)
//                setVideoSource(MediaRecorder.VideoSource.SURFACE)
//                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
//                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
//                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
//
//                // Use app-specific directory (no need for WRITE_EXTERNAL_STORAGE on Android 13+)
////                val videoFile = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "screen_recording.mp4")
//                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
//                val videoFile = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "screen_recording_$timestamp.mp4")
//                setOutputFile(videoFile.absolutePath)
//
//                prepare()
//                start()
//            }
//
//            Log.d("ScreenRecorder", "Recording started")
//        } catch (e: IOException) {
//            e.printStackTrace()
//            Log.e("ScreenRecorder", "Error starting recording: ${e.message}")
//        }
//    }
//
//    // This method stops the screen recording
//    fun stopRecording() {
//        try {
//            mediaRecorder?.apply {
//                stop()
//                release()
//            }
//            Log.d("ScreenRecorder", "Recording stopped")
//        } catch (e: Exception) {
//            Log.e("ScreenRecorder", "Error stopping recording: ${e.message}")
//        }
//    }
//
//    val surface get() = mediaRecorder?.surface
//}
//
////import android.content.Context
////import android.media.MediaRecorder
////import android.os.Environment
////import android.util.Log
////import java.io.File
////import java.io.IOException
////
////class ScreenRecorder(private val context: Context) {
////
////    private var mediaRecorder: MediaRecorder? = null
////
////    // This method starts the screen recording process
////    fun startRecording() {
////        try {
////            mediaRecorder = MediaRecorder()
////
////            // Setup media recorder
////            mediaRecorder?.apply {
////                setAudioSource(MediaRecorder.AudioSource.MIC)
////                setVideoSource(MediaRecorder.VideoSource.SURFACE)
////                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
////                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
////                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
////
////                val videoFile = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "screen_recording.mp4")
////                setOutputFile(videoFile.absolutePath)
////
////                prepare()
////                start()
////            }
////
////            Log.d("ScreenRecorder", "Recording started")
////        } catch (e: IOException) {
////            e.printStackTrace()
////            Log.e("ScreenRecorder", "Error starting recording: ${e.message}")
////        }
////    }
////
////    // This method stops the screen recording
////    fun stopRecording() {
////        try {
////            mediaRecorder?.apply {
////                stop()
////                release()
////            }
////            Log.d("ScreenRecorder", "Recording stopped")
////        } catch (e: Exception) {
////            Log.e("ScreenRecorder", "Error stopping recording: ${e.message}")
////        }
////    }
////
////    val surface get() = mediaRecorder?.surface
////}
