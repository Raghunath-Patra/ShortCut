////package com.example.shortcutdemo
////
////import android.app.Activity
////import android.content.Context
////import android.media.projection.MediaProjection
////import android.media.projection.MediaProjectionManager
////import android.content.Context.*
////import android.content.Intent
////import android.os.Bundle
////import android.widget.Toast
////
////class ScreenCaptureActivity : Activity() {
////
////    private val REQUEST_CODE_SCREEN_CAPTURE = 1
////
////    override fun onCreate(savedInstanceState: Bundle?) {
////        super.onCreate(savedInstanceState)
////
////        // Launch the screen capture request
////        val mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
////        val captureIntent = mediaProjectionManager.createScreenCaptureIntent()
////        startActivityForResult(captureIntent, REQUEST_CODE_SCREEN_CAPTURE)
////    }
////
////    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
////        super.onActivityResult(requestCode, resultCode, data)
////
////        if (requestCode == REQUEST_CODE_SCREEN_CAPTURE) {
////            if (resultCode == RESULT_OK && data != null) {
////                // Pass the result back to the Service
//////                val serviceIntent = Intent(this, ScreenRecordingService::class.java)
//////                serviceIntent.action = "START_RECORDING"
//////                serviceIntent.putExtra("resultCode", resultCode)
//////                serviceIntent.putExtra("data", data)
////                val serviceIntent = Intent(this, ScreenRecordingService::class.java)
////                serviceIntent.action = "START_RECORDING"
////                serviceIntent.putExtra("resultCode", resultCode)
////                serviceIntent.putExtra("data", data)  // The intent from MediaProjectionManager
////                startService(serviceIntent)
////
////                // Start the service to handle recording
////                startService(serviceIntent)
////            } else {
////                Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show()
////            }
////
////            // Close the activity after the result is handled
////            finish()
////        }
////    }
////}
//
//package com.example.shortcutdemo
//
//import android.app.Activity
//import android.content.Context
//import android.content.Intent
//import android.media.projection.MediaProjectionManager
//import android.os.Bundle
//import android.util.Log
//import android.widget.Toast
//import androidx.core.content.edit
//
//class ScreenCaptureActivity : Activity() {
//    private val TAG = "ScreenCaptureActivity"
//    private val REQUEST_CODE = 1001
//    val PREFS_NAME = "app_preferences"
//    val KEY_RESULT_CODE = "result_code"
//    val KEY_DATA_INTENT = "data_intent"
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//
//        // Request screen capture permission
//        val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
//        startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), REQUEST_CODE)
//    }
//
//    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
//        super.onActivityResult(requestCode, resultCode, data)
//
//        if (requestCode == REQUEST_CODE) {
//            if (resultCode == RESULT_OK && data != null) {
//                // Save the permission result in SharedPreferences
//                val sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
//                sharedPreferences.edit{
//                    putInt(KEY_RESULT_CODE, resultCode)
//                    putString(KEY_DATA_INTENT, data.toUri(0))
//                }
//
//                // Start the recording service
//                val intent = Intent(this, ScreenRecordingService::class.java)
//                intent.action = ScreenRecordingService.ACTION_START
//                startService(intent)
//
//                Toast.makeText(this, "Screen recording started", Toast.LENGTH_SHORT).show()
//            } else {
//                Log.d(TAG, "Screen recording permission denied")
//                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
//            }
//        }
//
//        // Always finish the transparent activity
//        finish()
//    }
//}
