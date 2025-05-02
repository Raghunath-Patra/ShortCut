////package com.example.shortcutdemo
////
////import android.os.Bundle
////import androidx.activity.ComponentActivity
////import androidx.activity.compose.setContent
////import androidx.activity.enableEdgeToEdge
////import androidx.compose.foundation.layout.fillMaxSize
////import androidx.compose.foundation.layout.padding
////import androidx.compose.material3.Scaffold
////import androidx.compose.material3.Text
////import androidx.compose.runtime.Composable
////import androidx.compose.ui.Modifier
////import androidx.compose.ui.tooling.preview.Preview
////import com.example.shortcutdemo.ui.theme.ShortCutDemoTheme
////
////class MainActivity : ComponentActivity() {
////    override fun onCreate(savedInstanceState: Bundle?) {
////        super.onCreate(savedInstanceState)
////        enableEdgeToEdge()
////        setContent {
////            ShortCutDemoTheme {
////                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
////                    Greeting(
////                        name = "Android",
////                        modifier = Modifier.padding(innerPadding)
////                    )
////                }
////            }
////        }
////    }
////}
////
////@Composable
////fun Greeting(name: String, modifier: Modifier = Modifier) {
////    Text(
////        text = "Hello $name!",
////        modifier = modifier
////    )
////}
////
////@Preview(showBackground = true)
////@Composable
////fun GreetingPreview() {
////    ShortCutDemoTheme {
////        Greeting("Android")
////    }
////}
//
//package com.example.shortcutdemo
//
//import android.content.Intent
//import android.net.Uri
//import android.os.Build
//import android.os.Bundle
//import android.provider.Settings
//import android.widget.Button
//import android.widget.Toast
//import androidx.appcompat.app.AppCompatActivity
//
//class MainActivity : AppCompatActivity() {
//
//    companion object {
//        private const val OVERLAY_PERMISSION_REQ_CODE = 1234
//    }
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        supportActionBar?.hide()
//        setContentView(R.layout.activity_main)
//
//        val createShortcutBtn = findViewById<Button>(R.id.createShortcutBtn)
//        val removeShortcutBtn = findViewById<Button>(R.id.removeShortcutBtn)
//
//        createShortcutBtn.setOnClickListener {
//            // Check if we have overlay permission
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
//                // Request the permission
//                val intent = Intent(
//                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
//                    Uri.parse("package:$packageName")
//                )
//                startActivityForResult(intent, OVERLAY_PERMISSION_REQ_CODE)
//            } else {
//                // We already have permission, start the floating service
//                startFloatingButtonService()
//            }
//        }
//
//        removeShortcutBtn.setOnClickListener {
//            // Stop the floating button service
//            stopService(Intent(this, FloatingButtonService::class.java))
//            Toast.makeText(this, "Floating shortcut removed", Toast.LENGTH_SHORT).show()
//        }
//    }
//
//    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
//        super.onActivityResult(requestCode, resultCode, data)
//        if (requestCode == OVERLAY_PERMISSION_REQ_CODE) {
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
//                startFloatingButtonService()
//            } else {
//                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
//            }
//        }
//    }
//
//    private fun startFloatingButtonService() {
//        val serviceIntent = Intent(this, FloatingButtonService::class.java)
//        startService(serviceIntent)
//        Toast.makeText(this, "Floating shortcut created", Toast.LENGTH_SHORT).show()
//    }
//}

package com.example.shortcutdemo

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    companion object {
        private const val OVERLAY_PERMISSION_REQ_CODE = 1234
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_main)

        val createShortcutBtn = findViewById<Button>(R.id.createShortcutBtn)
        val removeShortcutBtn = findViewById<Button>(R.id.removeShortcutBtn)

        // Check permission status and log it
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true // On older versions, permission is granted at install time
        }
        Log.d(TAG, "Overlay permission status: $hasPermission")

        createShortcutBtn.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                try {
                    // Request the permission using ACTION_MANAGE_OVERLAY_PERMISSION
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                    intent.data = Uri.parse("package:$packageName")
                    startActivityForResult(intent, OVERLAY_PERMISSION_REQ_CODE)
                    Toast.makeText(this, "Please grant the permission", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Log.e(TAG, "Error opening permission screen: ${e.message}")
                    Toast.makeText(this, "Error requesting permission: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } else {
                // We already have permission, start the floating service
                startFloatingButtonService()
            }
        }

        removeShortcutBtn.setOnClickListener {
            // Stop the floating button service
            stopService(Intent(this, FloatingButtonService::class.java))
            Toast.makeText(this, "Floating shortcut removed", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        // Check permission again when activity resumes
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
        Log.d(TAG, "Overlay permission status (onResume): $hasPermission")
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_PERMISSION_REQ_CODE) {
            // Check if permission is granted after returning from settings
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val hasPermission = Settings.canDrawOverlays(this)
                Log.d(TAG, "Overlay permission after request: $hasPermission")

                if (hasPermission) {
                    startFloatingButtonService()
                } else {
                    Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun startFloatingButtonService() {
        try {
            val serviceIntent = Intent(this, FloatingButtonService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            Toast.makeText(this, "Floating shortcut created", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error starting service: ${e.message}")
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}