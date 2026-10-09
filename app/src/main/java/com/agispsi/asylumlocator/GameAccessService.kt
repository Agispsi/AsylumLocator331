package com.agispsi.asylumlocator

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.PointF
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class GameAccessService : AccessibilityService() {
    companion object { @Volatile var instance: GameAccessService? = null; const val GAME = "com.phs.global" }
    override fun onServiceConnected() { instance = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) { }
    override fun onInterrupt() { CaptureService.instance?.cancel("Accessibility interrupted") }
    override fun onDestroy() { instance = null; CaptureService.instance?.cancel("Accessibility disconnected"); super.onDestroy() }
    fun gameForeground(): Boolean = windows.any { window ->
        window.type == AccessibilityWindowInfo.TYPE_APPLICATION && (window.isActive || window.isFocused) && window.root?.packageName?.toString() == GAME
    }
    suspend fun gesture(from: PointF, to: PointF = from, duration: Long = 90): Boolean {
        check(gameForeground()) { "Last Asylum is not foreground. Return to the game, then restart." }
        return suspendCancellableCoroutine { continuation ->
            val path = Path().apply { moveTo(from.x, from.y); if (from != to) lineTo(to.x, to.y) }
            val description = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, duration)).build()
            val dispatched = dispatchGesture(description, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) { if (continuation.isActive) continuation.resume(true) }
                override fun onCancelled(gestureDescription: GestureDescription?) { if (continuation.isActive) continuation.resume(false) }
            }, null)
            if (!dispatched && continuation.isActive) continuation.resume(false)
        }
    }
}
