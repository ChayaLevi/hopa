package com.hopa.feature.easy

import com.hopa.stores.ConversationStore

/** What the Easy history page reads. */
interface EasyDependencies {
    val conversations: ConversationStore
}
