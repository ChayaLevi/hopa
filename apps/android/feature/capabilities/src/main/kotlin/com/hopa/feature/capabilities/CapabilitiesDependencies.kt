package com.hopa.feature.capabilities

import android.content.Context
import com.hopa.runlog.store.RunRecorder
import com.hopa.stores.MemoryStore
import com.hopa.stores.SettingsRepository
import com.hopa.stores.SkillStore

/** What the capabilities page reads. */
interface CapabilitiesDependencies {
    val context: Context
    val recorder: RunRecorder
    val memory: MemoryStore
    val skills: SkillStore
    val settings: SettingsRepository
}
