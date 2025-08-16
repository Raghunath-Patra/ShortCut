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

    // Screen dimensions
    private var screenWidth: Int = 0
    private var screenHeight: Int = 0
    
    // Auto-hide functionality
    private val hideHandler = Handler(Looper.getMainLooper())
    private val autoHideRunnable = Runnable { hideToEdge() }

    // Views
    private lateinit var floatingButton: View
    private lateinit var shortcutIcon: View

    companion object {
        private const val CHANNEL_ID = "floating_shortcut_channel"
        private const val NOTIFICATION_ID = 1
        private const val TAG = "FloatingButtonService"
        private const val CLICK_DRAG_TOLERANCE = 20f
        private const val DRAG_INWARD_THRESHOLD = 100f
        private const val AUTO_HIDE_DELAY = 3000L // 3 seconds
        private const val EDGE_MARGIN = 8 // Margin from screen edge
        private const val HIDDEN_OFFSET = 40 // How much to hide when at edge
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

            // Initial position (left edge, center vertically)
            layoutParams.gravity = Gravity.TOP or Gravity.START
            layoutParams.x = -HIDDEN_OFFSET // Start partially hidden
            layoutParams.y = screenHeight / 2 - 50 // Center vertically

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
                    // Cancel auto-hide and show button fully
                    cancelAutoHide()
                    showButton()

                    // Record initial positions
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    
                    isDragging = false
                    hasMoved = false

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
                    }

                    if (isDragging) {
                        // Update position during drag
                        layoutParams.x = (initialX + deltaX).toInt()
                        layoutParams.y = (initialY + deltaY).toInt()
                        
                        // Keep within screen bounds
                        layoutParams.x = max(-HIDDEN_OFFSET, min(screenWidth - 48 + HIDDEN_OFFSET, layoutParams.x))
                        layoutParams.y = max(0, min(screenHeight - 48, layoutParams.y))
                        
                        windowManager.updateViewLayout(floatingButtonView, layoutParams)
                        
                        // Check for drag inward gesture
                        checkDragInward(deltaX, deltaY)
                    }
                    
                    return@setOnTouchListener true
                }

                MotionEvent.ACTION_UP -> {
                    animateButtonPress(false)
                    
                    if (!hasMoved) {
                        // It's a tap/click
                        Log.d(TAG, "Button tapped")
                        launchFloatingWindow()
                    } else if (isDragging) {
                        // End of drag - snap to nearest edge
                        snapToEdge()
                    }
                    
                    // Reset states
                    isDragging = false
                    hasMoved = false
                    
                    // Restart auto-hide timer
                    startAutoHideTimer()
                    
                    return@setOnTouchListener true
                }
                
                MotionEvent.ACTION_CANCEL -> {
                    animateButtonPress(false)
                    isDragging = false
                    hasMoved = false
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
                .scaleX(1.1f)
                .scaleY(1.1f)
                .setDuration(100)
                .start()
                
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
                
            shortcutIcon.animate()
                .alpha(0f)
                .setDuration(100)
                .start()
        }
    }

    private fun checkDragInward(deltaX: Float, deltaY: Float) {
        val isOnLeftEdge = layoutParams.x <= EDGE_MARGIN
        val isOnRightEdge = layoutParams.x >= screenWidth - 48 - EDGE_MARGIN
        
        val dragInwardThreshold = DRAG_INWARD_THRESHOLD
        
        if ((isOnLeftEdge && deltaX > dragInwardThreshold) || 
            (isOnRightEdge && deltaX < -dragInwardThreshold)) {
            
            Log.d(TAG, "Drag inward detected - launching window")
            launchFloatingWindow()
            
            // Reset position to prevent further triggers
            isDragging = false
        }
    }

    private fun snapToEdge() {
        val centerX = layoutParams.x + 24 // 24 is half of button width (48dp)
        val snapToLeft = centerX < screenWidth / 2
        
        val targetX = if (snapToLeft) {
            -HIDDEN_OFFSET
        } else {
            screenWidth - 48 + HIDDEN_OFFSET
        }
        
        // Animate to edge
        val currentX = layoutParams.x
        val handler = Handler(Looper.getMainLooper())
        
        val animator = object : Runnable {
            var step = 0
            val totalSteps = 10
            
            override fun run() {
                if (step < totalSteps) {
                    val progress = step.toFloat() / totalSteps
                    // Ease out animation
                    val easedProgress = 1 - (1 - progress) * (1 - progress)
                    
                    layoutParams.x = (currentX + (targetX - currentX) * easedProgress).toInt()
                    windowManager.updateViewLayout(floatingButtonView, layoutParams)
                    
                    step++
                    handler.postDelayed(this, 16) // ~60fps
                } else {
                    layoutParams.x = targetX
                    windowManager.updateViewLayout(floatingButtonView, layoutParams)
                }
            }
        }
        
        handler.post(animator)
    }

    private fun showButton() {
        val isOnLeftEdge = layoutParams.x <= 0
        val targetX = if (isOnLeftEdge) EDGE_MARGIN else screenWidth - 48 - EDGE_MARGIN
        
        floatingButton.animate()
            .translationX((targetX - layoutParams.x).toFloat())
            .setDuration(200)
            .start()
    }

    private fun hideToEdge() {
        floatingButton.animate()
            .translationX(0f)
            .setDuration(300)
            .start()
    }

    private fun startAutoHideTimer() {
        cancelAutoHide()
        hideHandler.postDelayed(autoHideRunnable, AUTO_HIDE_DELAY)
    }

    private fun cancelAutoHide() {
        hideHandler.removeCallbacks(autoHideRunnable)
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
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
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