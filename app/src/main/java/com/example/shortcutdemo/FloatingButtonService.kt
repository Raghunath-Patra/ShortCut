package com.example.shortcutdemo

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
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
    private var initialX: Int = 0
    private var initialY: Int = 0
    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()

        // Create a persistent notification to keep the service running
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Floating Shortcut")
            .setContentText("Tap to open the floating window")
            .setSmallIcon(R.drawable.ic_shortcut)
            .build()

        startForeground(NOTIFICATION_ID, notification)

        // Create the floating button
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        floatingButtonView = LayoutInflater.from(this).inflate(R.layout.floating_button, null)

        // Set up the window parameters
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        // Initial position on screen
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 100

        // Add the view to the window
        windowManager.addView(floatingButtonView, params)

        // Set up touch listener for drag and click
        val floatingButton = floatingButtonView.findViewById<ImageView>(R.id.floatingButton)
        floatingButton.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // Get initial position
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    return@setOnTouchListener true
                }
                MotionEvent.ACTION_MOVE -> {
                    // Calculate new position
                    //params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.x = 0
                    params.y = initialY + (event.rawY - initialTouchY).toInt()

                    // Update the layout
                    windowManager.updateViewLayout(floatingButtonView, params)
                    return@setOnTouchListener true
                }
                MotionEvent.ACTION_UP -> {
                    // Check if it was a click (small movement)
                    if (Math.abs(initialTouchX - event.rawX) < 10 && Math.abs(initialTouchY - event.rawY) < 10) {
                        // Launch floating window activity
                        val intent = Intent(this, FloatingWindowActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        startActivity(intent)
                    }
                    return@setOnTouchListener true
                }
                else -> return@setOnTouchListener false
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Remove the floating view
        if (::windowManager.isInitialized && ::floatingButtonView.isInitialized) {
            windowManager.removeView(floatingButtonView)
        }
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

    companion object {
        private const val CHANNEL_ID = "floating_shortcut_channel"
        private const val NOTIFICATION_ID = 1
    }
}