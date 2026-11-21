package com.hopa.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import com.google.firebase.FirebaseApp
import com.hopa.app.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class HopaApplication : Application() {
    @Inject lateinit var container: AppContainer

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            // A loaded model is hundreds of megabytes; release it before the
            // system kills the process for it. It reloads on the next turn.
            runCatching { container.releaseLocalModel() }
        }
    }

    override fun onCreate() {
        super.onCreate()
        // Seed the shared access state before any screen asks for it. A process
        // started by the accessibility service may not get a fresh ON_RESUME.
        com.hopa.capabilities.accessibility.state.AccessibilityState.refresh(this)
        // An approval on disk means the process died while waiting: deny it
        // rather than resurrect a question whose turn is gone.
        scope.launch {
            runCatching { container.pendingApprovals.clearAll() }
        }
        // A fresh install has no FCM token until something asks for one, and the
        // gateway needs it in device.hello before it can wake this phone.
        if (FirebaseApp.getApps(this).isNotEmpty()) {
            container.appScope.launch { container.backend.loadPushToken() }
        }
    }
}
