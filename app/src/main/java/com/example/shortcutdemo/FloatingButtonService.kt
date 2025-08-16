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
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlin.math.abs

class FloatingButtonService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingButtonView: View
    private lateinit var layoutParams: WindowManager.LayoutParams
    private var initialX: Int = 0
    private var initialY: Int = 0
    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f

    // Small drag threshold for inward activation (30 pixels is "slight")
    private val inwardDragThreshold = 30f

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
                .setContentText("Drag the sidebar inward slightly to access recording controls")
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
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            )

            // Initial position on screen (slightly away from left edge to avoid back gesture)
            layoutParams.gravity = Gravity.TOP or Gravity.START
            layoutParams.x = 8  // Move away from very edge to avoid system back gesture
            layoutParams.y = 100

            // For Android 10+ (API 29+), set additional properties to avoid gesture conflicts
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                layoutParams.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }

            // Add the view to the window
            windowManager.addView(floatingButtonView, layoutParams)

            // Set up touch listener for drag
            setupTouchListener()

            Log.d(TAG, "Floating button created successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error creating floating button", e)
            stopSelf()
        }
    }

    private fun setupTouchListener() {
        val floatingButton = floatingButtonView.findViewById<View>(R.id.floatingButton)
        floatingButton.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // Store initial position
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY

                    // Request that parent views don't intercept touch events
                    view.parent?.requestDisallowInterceptTouchEvent(true)

                    // Immediately consume the event to prevent system gestures
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - initialTouchX
                    val deltaY = event.rawY - initialTouchY

                    // Only allow vertical movement (keep it near the left edge)
                    layoutParams.x = 8 // Keep it slightly away from the edge
                    layoutParams.y = initialY + deltaY.toInt()

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

                    // Always consume move events to prevent system gesture detection
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val deltaX = event.rawX - initialTouchX
                    val deltaY = event.rawY - initialTouchY

                    Log.d(TAG, "Touch ended: deltaX=$deltaX, deltaY=$deltaY, threshold=$inwardDragThreshold")

                    // Check if dragged inward (to the right) slightly
                    if (deltaX > inwardDragThreshold) {
                        // Dragged inward enough - launch floating window
                        Log.d(TAG, "Inward drag detected, launching window")
                        launchFloatingWindow()
                    } else {
                        Log.d(TAG, "Drag not sufficient to trigger action")
                    }

                    // Allow parent views to intercept again
                    view.parent?.requestDisallowInterceptTouchEvent(false)

                    // Always consume the event
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    // Handle cancelled touches (system might cancel due to gesture detection)
                    Log.d(TAG, "Touch cancelled by system")
                    true
                }
                else -> {
                    // Consume all other touch events
                    true
                }
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