package com.example.shortcutdemo

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
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
import kotlin.math.max
import kotlin.math.min

class FloatingButtonService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingButtonView: View
    private lateinit var layoutParams: WindowManager.LayoutParams

    // Touch event variables
    private var initialX: Int = 0
    private var initialY: Int = 0
    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f
    private var isDragging: Boolean = false
    private var hasMoved: Boolean = false
    private var isRepositioning: Boolean = false // Track if user is repositioning vertically

    // Screen dimensions
    private var screenWidth: Int = 0
    private var screenHeight: Int = 0
    
    // Auto-hide functionality
    private val hideHandler = Handler(Looper.getMainLooper())
    private val autoHideRunnable = Runnable { hideToEdge() }

    // Views
    private lateinit var floatingButton: View
    private lateinit var shortcutIcon: View
    private lateinit var circleBackground: View
    private lateinit var expandedBackground: View
    
    // Window state tracking
    private var isWindowLaunched = false

    companion object {
        private const val CHANNEL_ID = "floating_shortcut_channel"
        private const val NOTIFICATION_ID = 1
        private const val TAG = "FloatingButtonService"
        private const val CLICK_DRAG_TOLERANCE = 20f
        private const val DRAG_INWARD_THRESHOLD = 50f
        private const val VERTICAL_DRAG_THRESHOLD = 30f // Threshold to detect vertical repositioning
        private const val AUTO_HIDE_DELAY = 3000L // 3 seconds
        private const val EDGE_MARGIN = 8 // Margin from screen edge
        private const val HIDDEN_OFFSET = 24 // How much to hide when at edge (3/4 of button width)
        private const val BUTTON_WIDTH_COLLAPSED = 32 // Button width when collapsed (dp)
        private const val BUTTON_WIDTH_EXPANDED = 48 // Button width when expanded (dp)
        private const val BUTTON_HEIGHT = 48 // Button height (dp)
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
            // Create a persistent notification
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Screen Recording Shortcut")
                .setContentText("Drag inward or tap to access controls")
                .setSmallIcon(R.drawable.ic_shortcut)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()

            startForeground(NOTIFICATION_ID, notification)

            // Inflate the view and get the window manager
            floatingButtonView = LayoutInflater.from(this).inflate(R.layout.floating_button, null)
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

            // Get references to views
            floatingButton = floatingButtonView.findViewById(R.id.floatingButton)
            shortcutIcon = floatingButtonView.findViewById(R.id.shortcutIcon)
            circleBackground = floatingButtonView.findViewById(R.id.circleBackground)
            expandedBackground = floatingButtonView.findViewById(R.id.expandedBackground)

            // Get screen dimensions
            val displayMetrics = DisplayMetrics()
            windowManager.defaultDisplay.getMetrics(displayMetrics)
            screenWidth = displayMetrics.widthPixels
            screenHeight = displayMetrics.heightPixels

            // Set up window parameters
            layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            )

            // Initial position (left edge, center vertically) - always on left
            layoutParams.gravity = Gravity.TOP or Gravity.START
            layoutParams.x = -HIDDEN_OFFSET // Start partially hidden on left edge
            layoutParams.y = screenHeight / 2 - (BUTTON_HEIGHT / 2) // Center vertically

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                layoutParams.layoutInDisplayCutoutMode = 
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }

            windowManager.addView(floatingButtonView, layoutParams)
            setupTouchListener()

            // Start auto-hide timer
            startAutoHideTimer()

            Log.d(TAG, "Floating button created successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error creating floating button", e)
            stopSelf()
        }
    }

    private fun setupTouchListener() {
        floatingButton.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // Cancel auto-hide and expand button
                    cancelAutoHide()
                    expandButton()

                    // Record initial positions
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    
                    isDragging = false
                    hasMoved = false
                    isRepositioning = false

                    // Visual feedback
                    animateButtonPress(true)
                    
                    return@setOnTouchListener true
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - initialTouchX
                    val deltaY = event.rawY - initialTouchY
                    
                    val distance = kotlin.math.sqrt(deltaX * deltaX + deltaY * deltaY)
                    
                    if (distance > CLICK_DRAG_TOLERANCE && !isDragging) {
                        isDragging = true
                        hasMoved = true
                        
                        // Determine the primary direction of movement
                        val absX = kotlin.math.abs(deltaX)
                        val absY = kotlin.math.abs(deltaY)
                        
                        if (absY > VERTICAL_DRAG_THRESHOLD && absY > absX) {
                            // Primarily vertical movement - user is repositioning
                            isRepositioning = true
                            Log.d(TAG, "Started repositioning (vertical drag) - deltaX: $deltaX, deltaY: $deltaY")
                        } else {
                            Log.d(TAG, "Started dragging (potential inward) - deltaX: $deltaX, deltaY: $deltaY")
                        }
                    }

                    if (isDragging) {
                        // Always allow vertical movement for repositioning
                        layoutParams.y = (initialY + deltaY).toInt()
                        
                        // Keep within screen bounds vertically
                        layoutParams.y = max(0, min(screenHeight - BUTTON_HEIGHT, layoutParams.y))
                        
                        windowManager.updateViewLayout(floatingButtonView, layoutParams)
                        
                        // Only check for inward drag if NOT repositioning
                        if (!isRepositioning) {
                            checkDragInward(deltaX)
                        }
                    }
                    
                    return@setOnTouchListener true
                }

                MotionEvent.ACTION_UP -> {
                    animateButtonPress(false)
                    
                    if (!hasMoved) {
                        // It's a tap/click - launch window only if not already launched
                        if (!isWindowLaunched) {
                            Log.d(TAG, "Button tapped")
                            launchFloatingWindow()
                        } else {
                            Log.d(TAG, "Button tapped but window already launched")
                        }
                    }
                    // Note: If dragged inward, window is already launched by checkDragInward()
                    
                    // Reset states
                    isDragging = false
                    hasMoved = false
                    isRepositioning = false
                    
                    // Restart auto-hide timer
                    startAutoHideTimer()
                    
                    return@setOnTouchListener true
                }
                
                MotionEvent.ACTION_CANCEL -> {
                    animateButtonPress(false)
                    isDragging = false
                    hasMoved = false
                    isRepositioning = false
                    startAutoHideTimer()
                    return@setOnTouchListener true
                }
            }
            false
        }
    }

    private fun animateButtonPress(pressed: Boolean) {
        if (pressed) {
            floatingButton.animate()
                .scaleX(1.02f)
                .scaleY(1.02f)
                .setDuration(100)
                .start()
                
            // Enhance icon visibility when pressed
            shortcutIcon.animate()
                .alpha(1f)
                .setDuration(100)
                .start()
        } else {
            floatingButton.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(100)
                .start()
                
            // Return to expanded state alpha
            shortcutIcon.animate()
                .alpha(0.8f)
                .setDuration(100)
                .start()
        }
    }

    private fun checkDragInward(deltaX: Float) {
        // Only trigger on rightward drag (inward from left edge) and not during repositioning
        Log.d(TAG, "Checking drag inward - deltaX: $deltaX, threshold: $DRAG_INWARD_THRESHOLD, isRepositioning: $isRepositioning")
        if (deltaX > DRAG_INWARD_THRESHOLD && !isWindowLaunched && !isRepositioning) {
            Log.d(TAG, "Drag inward detected - launching window")
            launchFloatingWindow()
            
            // Reset dragging state to prevent further triggers
            isDragging = false
            hasMoved = true // Mark as moved to prevent click action
        }
    }

    private fun expandButton() {
        // Show expanded background with light color
        expandedBackground.visibility = View.VISIBLE
        expandedBackground.animate()
            .alpha(1f)
            .scaleX(1.5f) // Expand width
            .setDuration(200)
            .start()
            
        // Hide the transparent background
        circleBackground.animate()
            .alpha(0f)
            .setDuration(200)
            .start()
            
        // Show icon with better visibility
        shortcutIcon.animate()
            .alpha(0.8f)
            .setDuration(200)
            .start()
            
        // Move button slightly more visible
        layoutParams.x = -HIDDEN_OFFSET / 3
        windowManager.updateViewLayout(floatingButtonView, layoutParams)
    }

    private fun hideToEdge() {
        // Hide expanded background and show transparent one
        expandedBackground.animate()
            .alpha(0f)
            .scaleX(1f)
            .setDuration(300)
            .withEndAction {
                expandedBackground.visibility = View.GONE
            }
            .start()
            
        // Show transparent background
        circleBackground.animate()
            .alpha(1f)
            .setDuration(300)
            .start()
            
        // Hide icon
        shortcutIcon.animate()
            .alpha(0f)
            .setDuration(300)
            .start()
            
        // Move button back to mostly hidden position
        layoutParams.x = -HIDDEN_OFFSET
        windowManager.updateViewLayout(floatingButtonView, layoutParams)
    }

    private fun startAutoHideTimer() {
        cancelAutoHide()
        hideHandler.postDelayed(autoHideRunnable, AUTO_HIDE_DELAY)
    }

    private fun startWindowStateChecker() {
        // Check periodically if the FloatingWindowActivity is still running
        val handler = Handler(Looper.getMainLooper())
        val checker = object : Runnable {
            override fun run() {
                if (isWindowLaunched && !isFloatingWindowActivityRunning()) {
                    Log.d(TAG, "FloatingWindowActivity closed, resetting state")
                    isWindowLaunched = false
                } else if (isWindowLaunched) {
                    // Continue checking every 2 seconds while window is supposed to be open
                    handler.postDelayed(this, 2000)
                }
            }
        }
        handler.postDelayed(checker, 2000)
    }
    
    private fun isFloatingWindowActivityRunning(): Boolean {
        val activityManager = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
        val runningTasks = activityManager.getRunningTasks(10)
        
        for (task in runningTasks) {
            if (task.topActivity?.className == "com.example.shortcutdemo.FloatingWindowActivity") {
                return true
            }
        }
        return false
    }
    
    // Public method that can be called by FloatingWindowActivity when it closes
    fun onFloatingWindowClosed() {
        Log.d(TAG, "FloatingWindowActivity reported closure")
        isWindowLaunched = false
    }

    private fun launchFloatingWindow() {
        try {
            if (isWindowLaunched) {
                Log.d(TAG, "Window already launched, ignoring request")
                return
            }
            
            val intent = Intent(this, FloatingWindowActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                          Intent.FLAG_ACTIVITY_SINGLE_TOP or
                          Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
            isWindowLaunched = true
            Log.d(TAG, "FloatingWindowActivity launched")
            
            // Start checking for window closure
            startWindowStateChecker()
        } catch (e: Exception) {
            Log.e(TAG, "Error launching FloatingWindowActivity", e)
            isWindowLaunched = false
        }
    }

    private fun cancelAutoHide() {
        hideHandler.removeCallbacks(autoHideRunnable)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Handle window closed notification
        if (intent?.getBooleanExtra("window_closed", false) == true) {
            onFloatingWindowClosed()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onCreate()
        Log.d(TAG, "FloatingButtonService destroyed")
        cancelAutoHide()
        
        try {
            if (::windowManager.isInitialized && ::floatingButtonView.isInitialized) {
                windowManager.removeView(floatingButtonView)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing floating view", e)
        }
    }
}