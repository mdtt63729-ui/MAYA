package com.aistudio.mj.wxyt.accessibility

import android.accessibilityservice.AccessibilityService
import android.util.Log
import com.aistudio.mj.wxyt.domain.jarvis.MayaScreenContext
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class ORBAccessibilityService : AccessibilityService() {

    companion object {
        var shouldAutoClick = false
            set(value) {
                field = value
                if (value) {
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        field = false
                    }, 10000) // Reset after 10 seconds to give WhatsApp time to load
                }
            }
        var instance: ORBAccessibilityService? = null

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


        fun setTextOnFocusedField(text: String): Boolean {
            val inst = instance ?: return false
            val root = inst.rootInActiveWindow ?: return false
            val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
            val args = android.os.Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
            return focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        }

        /**
         * Searches the current screen for any clickable element whose text, content
         * description, or view ID contains the given query. Performs a gesture click
         * on the first match. Used for "click on Search", "Send চাপো" etc.
         */
        fun clickTextOnScreen(text: String): Boolean {
            val inst = instance ?: return false
            val root = inst.rootInActiveWindow ?: return false

            // Attempt 1: by text match (case-insensitive)
            val nodes = root.findAccessibilityNodeInfosByText(text)
            for (node in nodes) {
                var current: AccessibilityNodeInfo? = node
                while (current != null) {
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

            // Attempt 2: by content description match (case-insensitive)
            val descMatch = findByContentDescription(root, text)
            if (descMatch != null) {
                val bounds = android.graphics.Rect()
                descMatch.getBoundsInScreen(bounds)
                if (!bounds.isEmpty) {
                    if (dispatchGestureClick(bounds.centerX().toFloat(), bounds.centerY().toFloat())) return true
                }
                if (performClick(descMatch)) return true
            }

            return false
        }

        /**
         * Recursively searches the accessibility tree for a node whose content
         * description contains the query (case-insensitive).
         */
        private fun findByContentDescription(node: AccessibilityNodeInfo, query: String): AccessibilityNodeInfo? {
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""
            if (desc.contains(query.lowercase())) return node
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                val found = findByContentDescription(child, query)
                if (found != null) return found
            }
            return null
        }

        /**
         * Attempts to click a node — first via gesture, then ACTION_CLICK,
         * then by walking up the parent chain.
         */
        private fun performClick(node: AccessibilityNodeInfo): Boolean {
            // Real visual click first (gesture-based)
            val bounds = android.graphics.Rect()
            node.getBoundsInScreen(bounds)
            if (!bounds.isEmpty) {
                val x = bounds.centerX().toFloat()
                val y = bounds.centerY().toFloat()
                if (dispatchGestureClick(x, y)) {
                    return true
                }
            }

            // Fallback: ACTION_CLICK
            if (node.isClickable) {
                val success = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (success) return true
            }

            // Try parent if not clickable
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

        fun searchAndClickSendButton(node: AccessibilityNodeInfo): Boolean {
            val idsToTry = listOf(
                "com.whatsapp:id/send",
                "com.whatsapp.w4b:id/send",
                "com.whatsapp:id/send_button",
                "com.whatsapp.w4b:id/send_button"
            )
            for (id in idsToTry) {
                val sendButtons = node.findAccessibilityNodeInfosByViewId(id)
                if (sendButtons.isNotEmpty()) {
                    for (button in sendButtons) {
                        if (performClick(button)) {
                            Log.d("ORBAccessibility", "Clicked send button by ID: $id")
                            return true
                        }
                    }
                }
            }
            return recursiveSearchAndClick(node)
        }

        private fun recursiveSearchAndClick(node: AccessibilityNodeInfo): Boolean {
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""

            if (desc == "send" || desc == "send message" || desc == "bheje" || desc == "bhejen" || desc == "envio" || desc == "পাঠান" || desc == "পাঠাও") {
                if (performClick(node)) {
                    Log.d("ORBAccessibility", "Clicked send button by content description!")
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
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d("ORBAccessibility", "Accessibility Service Connected")
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        MayaScreenContext.update(event.packageName?.toString() ?: "", rootInActiveWindow)
        if (!shouldAutoClick) return

        val packageName = event.packageName?.toString() ?: ""
        if (packageName.contains("whatsapp")) {
            val rootNode = rootInActiveWindow ?: return
            val clicked = searchAndClickSendButton(rootNode)
            if (clicked) {
                Log.d("ORBAccessibility", "Successfully clicked WhatsApp send button")
                shouldAutoClick = false
            } else {
                // WhatsApp often emits the first window event before the composer
                // and send button have finished rendering. Retry against the live
                // accessibility tree instead of assuming that opening the chat sent it.
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    if (shouldAutoClick) {
                        val retryRoot = rootInActiveWindow
                        if (retryRoot != null && searchAndClickSendButton(retryRoot)) {
                            Log.d("ORBAccessibility", "Successfully clicked WhatsApp send button on retry")
                            shouldAutoClick = false
                        }
                    }
                }, 350L)
            }
        }
    }

    private fun searchAndClickSendButton(node: AccessibilityNodeInfo): Boolean {
        return Companion.searchAndClickSendButton(node)
    }

    private fun recursiveSearchAndClick(node: AccessibilityNodeInfo): Boolean {
        return Companion.recursiveSearchAndClick(node)
    }

    override fun onInterrupt() {
        Log.d("ORBAccessibility", "Accessibility Service Interrupted")
    }
}
