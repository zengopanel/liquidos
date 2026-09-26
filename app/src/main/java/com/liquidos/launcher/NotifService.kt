package com.liquidos.launcher

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/** خدمة صغيرة جداً: كل وظيفتها فتح الإشعارات وقفل الشاشة من لوحتك. لا تقرأ أي شيء. */
class NotifService : AccessibilityService() {
    override fun onServiceConnected() {
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    companion object {
        var instance: NotifService? = null
    }
}
