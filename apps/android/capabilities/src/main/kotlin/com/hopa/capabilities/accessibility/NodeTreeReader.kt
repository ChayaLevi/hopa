package com.hopa.capabilities.accessibility

import android.hardware.display.DisplayManager
import android.view.Display
import android.view.accessibility.AccessibilityNodeInfo
import com.hopa.engine.contracts.Observation
import com.hopa.engine.contracts.ObservationNode
import com.hopa.engine.contracts.ScreenSize
import com.hopa.engine.observation.ObservationHasher

class NodeTreeReader(
    private val service: HopaAccessibilityService,
    private val maxNodes: Int = 400,
    private val maxText: Int = 6_000,
    /** Read the window on this display instead of the one the person sees. */
    private val displayId: Int = -1,
    /** A root resolved by the caller, when the window's own ids are unreliable. */
    private val rootOverride: AccessibilityNodeInfo? = null,
) {
    val refs: MutableMap<String, AccessibilityNodeInfo> = mutableMapOf()
    private var counter = 0
    private var truncated = false
    private var focusedRef = ""
    private val texts = mutableListOf<String>()

    fun read(): Observation {
        refs.clear()
        counter = 0
        truncated = false
        focusedRef = ""
        texts.clear()
        val root = rootOverride ?: if (displayId >= 0) {
            service.windows
                .filter { it.displayId == displayId }
                .firstNotNullOfOrNull { window -> window.root }
        } else {
            service.activeRoot()
        }
        val tree = if (root == null) {
            ObservationNode(type = "root")
        } else {
            convert(root)
        }
        val display = runCatching {
            val manager = service.getSystemService(DisplayManager::class.java)
            val target = if (displayId >= 0) displayId else Display.DEFAULT_DISPLAY
            manager?.getDisplay(target)
        }.getOrNull()
        val metrics = android.util.DisplayMetrics().also {
            if (display != null) display.getRealMetrics(it) else it.setTo(service.resources.displayMetrics)
        }
        val observation = Observation(
            id = "obs-${System.currentTimeMillis()}",
            timestamp = System.currentTimeMillis(),
            windowId = root?.windowId?.toString() ?: "active",
            displayId = if (displayId >= 0) displayId else display?.displayId ?: Display.DEFAULT_DISPLAY,
            rotation = display?.rotation ?: 0,
            density = metrics.density,
            screenSize = ScreenSize(metrics.widthPixels, metrics.heightPixels),
            focus = focusedRef,
            text = texts.joinToString("\n").take(maxText),
            tree = tree,
            app = root?.packageName?.toString() ?: "",
            truncated = truncated,
        )
        return observation.copy(hash = ObservationHasher.hash(observation))
    }

    private fun convert(info: AccessibilityNodeInfo): ObservationNode {
        if (counter >= maxNodes) {
            truncated = true
            return ObservationNode(type = "truncated")
        }
        val ref = "e${++counter}"
        if (ref.isNotEmpty()) {
            refs[ref] = info
        }
        val text = info.text?.toString().orEmpty()
        val description = info.contentDescription?.toString().orEmpty()
        if (text.isNotEmpty()) {
            texts += text
        }
        if (description.isNotEmpty() && description != text) {
            texts += description
        }
        if (info.isFocused && ref.isNotEmpty()) {
            focusedRef = ref
        }
        val children = mutableListOf<ObservationNode>()
        for (index in 0 until info.childCount) {
            if (counter >= maxNodes) {
                truncated = true
                break
            }
            val child = info.getChild(index) ?: continue
            children += convert(child)
        }
        val bounds = android.graphics.Rect()
        info.getBoundsInScreen(bounds)
        return ObservationNode(
            ref = ref,
            type = info.className?.toString()?.substringAfterLast('.') ?: "node",
            text = text,
            description = description,
            id = info.viewIdResourceName.orEmpty(),
            bounds = listOf(bounds.left, bounds.top, bounds.right, bounds.bottom),
            clickable = info.isClickable,
            enabled = info.isEnabled,
            checked = info.isChecked,
            scrollable = info.isScrollable,
            visible = info.isVisibleToUser,
            focused = info.isFocused,
            children = children,
        )
    }
}
