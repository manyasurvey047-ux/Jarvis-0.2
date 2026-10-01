package com.example.accessibility

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class ZoyaAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "ZoyaAccessibility"
        private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        
        var shouldAutoClick = false
            set(value) {
                field = value
                if (value) {
                    startAutoSendPolling()
                    mainHandler.postDelayed({
                        field = false
                    }, 12000) // Reset after 12 seconds
                }
            }
        var targetAppName = "whatsapp"
        var instance: ZoyaAccessibilityService? = null

        private fun startAutoSendPolling() {
            var attempts = 0
            val maxAttempts = 25 // 25 attempts * 300ms = 7.5 seconds of active checking
            val pollRunnable = object : Runnable {
                override fun run() {
                    if (!shouldAutoClick || attempts >= maxAttempts) {
                        return
                    }
                    attempts++
                    val inst = instance
                    if (inst != null) {
                        val root = inst.rootInActiveWindow
                        if (root != null) {
                            val pkg = root.packageName?.toString() ?: ""
                            if (pkg.contains("whatsapp", ignoreCase = true)) {
                                if (inst.searchAndClickSendButton(root)) {
                                    Log.d(TAG, "Polling: Successfully auto-sent WhatsApp message on attempt #$attempts")
                                    shouldAutoClick = false
                                    return
                                }
                            }
                        }
                    }
                    mainHandler.postDelayed(this, 300)
                }
            }
            mainHandler.postDelayed(pollRunnable, 600) // First check after 600ms
        }

        fun dispatchGestureClick(x: Float, y: Float): Boolean {
            val inst = instance ?: return false
            val path = android.graphics.Path()
            path.moveTo(x, y)
            path.lineTo(x, y)

            val builder = android.accessibilityservice.GestureDescription.Builder()
            builder.addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 50))
            val gesture = builder.build()

            return inst.dispatchGesture(gesture, null, null)
        }

        fun clickTextOnScreen(text: String): Boolean {
            val inst = instance ?: return false
            val root = inst.rootInActiveWindow ?: return false
            val nodes = root.findAccessibilityNodeInfosByText(text)
            for (node in nodes) {
                var current: AccessibilityNodeInfo? = node
                while(current != null) {
                    val bounds = android.graphics.Rect()
                    current.getBoundsInScreen(bounds)
                    if (!bounds.isEmpty && (current.isClickable || current == node)) {
                        val x = bounds.centerX().toFloat()
                        val y = bounds.centerY().toFloat()
                        if (dispatchGestureClick(x, y)) return true
                    }
                    current = current.parent
                }
            }
            return false
        }

        fun performBack(): Boolean {
            val inst = instance ?: return false
            return inst.performGlobalAction(GLOBAL_ACTION_BACK)
        }

        fun performHome(): Boolean {
            val inst = instance ?: return false
            return inst.performGlobalAction(GLOBAL_ACTION_HOME)
        }

        fun performRecents(): Boolean {
            val inst = instance ?: return false
            return inst.performGlobalAction(GLOBAL_ACTION_RECENTS)
        }

        fun performTakeScreenshot(): Boolean {
            val inst = instance ?: return false
            return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                inst.performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
            } else {
                false
            }
        }

        fun extractAllScreenText(): String {
            val inst = instance ?: return "Accessibility Service is not active. Enable it in Settings > Accessibility."
            val root = inst.rootInActiveWindow ?: return "Could not access current active window."
            val textList = mutableListOf<String>()

            fun collectText(node: AccessibilityNodeInfo?) {
                if (node == null) return
                val text = node.text?.toString()?.trim()
                val desc = node.contentDescription?.toString()?.trim()
                if (!text.isNullOrEmpty() && !textList.contains(text)) {
                    textList.add(text)
                }
                if (!desc.isNullOrEmpty() && !textList.contains(desc)) {
                    textList.add("[$desc]")
                }
                for (i in 0 until node.childCount) {
                    collectText(node.getChild(i))
                }
            }

            collectText(root)
            return if (textList.isEmpty()) {
                "No text or recognizable UI elements visible on screen."
            } else {
                "Visible screen content:\n" + textList.take(40).joinToString("\n• ")
            }
        }

        fun performScroll(direction: String): Boolean {
            val inst = instance ?: return false
            val metrics = android.content.res.Resources.getSystem().displayMetrics
            val width = metrics.widthPixels.toFloat().takeIf { it > 0 } ?: 1080f
            val height = metrics.heightPixels.toFloat().takeIf { it > 0 } ?: 2400f
            val centerX = width / 2f

            val lower = direction.lowercase()
            val startY: Float
            val endY: Float
            val duration: Long

            when {
                lower.contains("reel") || lower.contains("short") || lower.contains("next") -> {
                    // Fast swipe for Instagram Reels / YouTube Shorts
                    startY = height * 0.82f
                    endY = height * 0.18f
                    duration = 180
                }
                lower.contains("prev") || lower.contains("pehle") -> {
                    startY = height * 0.20f
                    endY = height * 0.80f
                    duration = 180
                }
                lower.contains("up") || lower.contains("top") -> {
                    // Scroll up (finger moves down)
                    startY = height * 0.30f
                    endY = height * 0.78f
                    duration = 280
                }
                else -> {
                    // Scroll down (finger moves up)
                    startY = height * 0.78f
                    endY = height * 0.30f
                    duration = 280
                }
            }

            val path = android.graphics.Path()
            path.moveTo(centerX, startY)
            path.lineTo(centerX, endY)

            val builder = android.accessibilityservice.GestureDescription.Builder()
            builder.addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, duration))
            val gesture = builder.build()

            return inst.dispatchGesture(gesture, null, null)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d("ZoyaAccessibility", "Accessibility Service Connected")
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !shouldAutoClick) return
        
        val packageName = event.packageName?.toString() ?: ""
        if (packageName.contains("whatsapp", ignoreCase = true)) {
            val rootNode = rootInActiveWindow ?: return
            val clicked = searchAndClickSendButton(rootNode)
            if (clicked) {
                Log.d("ZoyaAccessibility", "Event: Successfully clicked send button!")
                shouldAutoClick = false
            }
        }
    }

    fun searchAndClickSendButton(node: AccessibilityNodeInfo): Boolean {
        // Attempt 1: by all known WhatsApp Send View IDs across versions
        val idsToTry = listOf(
            "com.whatsapp:id/send",
            "com.whatsapp.w4b:id/send",
            "com.whatsapp:id/send_btn",
            "com.whatsapp:id/send_button",
            "com.whatsapp:id/voice_record_or_send_btn"
        )
        for (id in idsToTry) {
            val sendButtons = node.findAccessibilityNodeInfosByViewId(id)
            if (sendButtons.isNotEmpty()) {
                for (button in sendButtons) {
                    if (performClick(button)) {
                        Log.d("ZoyaAccessibility", "Clicked send button by ID: $id")
                        return true
                    }
                }
            }
        }

        // Attempt 2: Search by view text (in case button/label has Send/Bheje)
        val textCandidates = listOf("Send", "send", "Send message", "Bhejen", "Bheje", "भेजें", "भेजो")
        for (txt in textCandidates) {
            val found = node.findAccessibilityNodeInfosByText(txt)
            for (item in found) {
                val desc = item.contentDescription?.toString()?.lowercase() ?: ""
                val textVal = item.text?.toString()?.lowercase() ?: ""
                if (desc.contains("send") || desc.contains("bhej") || textVal.contains("send") || textVal.contains("bhej") || textVal.contains("भेज")) {
                    if (performClick(item)) {
                        Log.d("ZoyaAccessibility", "Clicked send button by text matching: $txt")
                        return true
                    }
                }
            }
        }

        // Attempt 3: Recursive search for Content Description or ImageButton at bottom-right
        return recursiveSearchAndClick(node)
    }

    private fun recursiveSearchAndClick(node: AccessibilityNodeInfo): Boolean {
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val viewId = node.viewIdResourceName?.lowercase() ?: ""
        
        if (desc.contains("send") || desc.contains("bheje") || desc.contains("bhejen") || 
            desc.contains("भेजें") || desc.contains("भेजो") || desc.contains("envio") || desc.contains("enviar") ||
            viewId.contains("send")) {
            if (performClick(node)) {
                Log.d("ZoyaAccessibility", "Clicked send button by recursive search!")
                return true
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                if (recursiveSearchAndClick(child)) {
                    return true
                }
            }
        }
        return false
    }

    private fun performClick(node: AccessibilityNodeInfo): Boolean {
        // 1. Action Click on Node
        if (node.isClickable) {
            val success = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            if (success) return true
        }

        // 2. Gesture Click via exact Screen Coordinates (Bypasses UI overlays)
        val bounds = android.graphics.Rect()
        node.getBoundsInScreen(bounds)
        if (!bounds.isEmpty && bounds.width() > 10 && bounds.height() > 10) {
            val x = bounds.centerX().toFloat()
            val y = bounds.centerY().toFloat()
            if (dispatchGestureClick(x, y)) {
                return true
            }
        }

        // 3. Try Parent if node itself wasn't clickable
        var parent = node.parent
        while (parent != null) {
            if (parent.isClickable) {
                val success = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (success) return true
            }
            parent = parent.parent
        }
        return false
    }

    override fun onInterrupt() {
        Log.d("ZoyaAccessibility", "Accessibility Service Interrupted")
    }
}
