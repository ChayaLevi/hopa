package com.hopa.capabilities.accessibility

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityManager

object AccessibilityState {
    fun isEnabled(context: Context): Boolean {
        val manager = context.getSystemService(AccessibilityManager::class.java)
        val listed = manager
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo.serviceInfo.packageName == context.packageName }
        if (listed) {
            return true
        }
        // The manager can keep a stale list for a while after the service is
        // rebound; a connected instance cannot exist without access being on.
        return HopaAccessibilityService.instance != null
    }

    fun openSettings(context: Context) {
        val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
