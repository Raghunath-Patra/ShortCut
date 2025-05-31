package com.example.shortcutdemo

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class FloatingButtonService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingButtonView: View
    private lateinit var layoutParams: WindowManager.LayoutParams
    private var initialX: Int = 0
    private var initialY: Int = 0
    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f

    companion object {
        private const val CHANNEL_ID = "floating_shortcut_channel"
        private const val NOTIFICATION_ID = 1
        private const val TAG = "FloatingButtonService"
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "FloatingButtonService created")

        createNotificationChannel()
        createFloatingButton()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName("Floating Shortcut Service")
                .setDescription("Enables the floating shortcut button")
                .build()

            NotificationManagerCompat.from(this).createNotificationChannel(channel)
        }
    }

    private fun createFloatingButton() {
        try {
            // Create a persistent notification to keep the service running
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Screen Recording Shortcut")
                .setContentText("Tap the floating button to access recording controls")
                .setSmallIcon(R.drawable.ic_shortcut)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()

            startForeground(NOTIFICATION_ID, notification)

            // Create the floating button
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            floatingButtonView = LayoutInflater.from(this).inflate(R.layout.floating_button, null)

            // Set up the window parameters
            layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )

            // Initial position on screen (right edge, middle)
            layoutParams.gravity = Gravity.TOP or Gravity.START
            layoutParams.x = 0
            layoutParams.y = 100

            // Add the view to the window
            windowManager.addView(floatingButtonView, layoutParams)

            // Set up touch listener for drag and click
            setupTouchListener()

            Log.d(TAG, "Floating button created successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error creating floating button", e)
            stopSelf()
        }
    }

    private fun setupTouchListener() {
        val floatingButton = floatingButtonView.findViewById<ImageView>(R.id.floatingButton)
        floatingButton.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // Store initial position
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    // Update position (only allow vertical movement)
                    layoutParams.x = 0 // Keep it on the left edge
                    layoutParams.y = initialY + (event.rawY - initialTouchY).toInt()

                    // Prevent moving outside screen bounds
                    val displayMetrics = resources.displayMetrics
                    if (layoutParams.y < 0) layoutParams.y = 0
                    if (layoutParams.y > displayMetrics.heightPixels - floatingButtonView.height) {
                        layoutParams.y = displayMetrics.heightPixels - floatingButtonView.height
                    }

                    // Update the layout
                    try {
                        windowManager.updateViewLayout(floatingButtonView, layoutParams)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error updating view layout", e)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    // Check if it was a click (small movement)
                    val deltaX = Math.abs(initialTouchX - event.rawX)
                    val deltaY = Math.abs(initialTouchY - event.rawY)

                    if (deltaX < 10 && deltaY < 10) {
                        // It's a click, launch floating window activity
                        launchFloatingWindow()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun launchFloatingWindow() {
        try {
            val intent = Intent(this, FloatingWindowActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            Log.d(TAG, "FloatingWindowActivity launched")
        } catch (e: Exception) {
            Log.e(TAG, "Error launching FloatingWindowActivity", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Return START_STICKY to restart service if killed
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "FloatingButtonService destroyed")

        // Remove the floating view
        try {
            if (::windowManager.isInitialized && ::floatingButtonView.isInitialized) {
                windowManager.removeView(floatingButtonView)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing floating view", e)
        }
    }
}