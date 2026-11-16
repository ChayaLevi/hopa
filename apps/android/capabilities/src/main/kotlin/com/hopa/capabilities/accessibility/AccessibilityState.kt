package com.hopa.capabilities.accessibility

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityManager

object AccessibilityState {
    fun isEnabled(context: Context): Boolean {
        // A connected instance cannot exist without access being on, and it is
        // the answer that survives a rebind; the manager's list can lag and its
        // entries are not always complete.
        if (HopaAccessibilityService.instance != null) {
            return true
        }
        return runCatching {
            val manager = context.getSystemService(AccessibilityManager::class.java)
            manager
                .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .any { it.resolveInfo.serviceInfo.packageName == context.packageName }
        }.getOrDefault(false)
    }

    fun openSettings(context: Context) {
        val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
