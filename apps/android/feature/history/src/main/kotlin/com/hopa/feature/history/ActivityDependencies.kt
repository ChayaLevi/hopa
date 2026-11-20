package com.hopa.feature.history

import android.content.Context
import com.hopa.runlog.store.RunRecorder
import com.hopa.runtime.RunGate
import com.hopa.stores.ConversationStore

/** What the activity screen needs. */
interface ActivityDependencies {
    val context: Context
    val conversations: ConversationStore
    val recorder: RunRecorder
    val runGate: RunGate
}
