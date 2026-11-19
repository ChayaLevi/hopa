package com.hopa.app.di

import android.content.Context
import com.hopa.app.runtime.RunGate
import com.hopa.runlog.store.RunRecorder
import com.hopa.stores.ConversationStore

/** What the activity/history screen needs. */
interface ActivityDependencies {
    val context: Context
    val conversations: ConversationStore
    val recorder: RunRecorder
    val runGate: RunGate
}
