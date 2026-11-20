package com.hopa.feature.usage

import com.hopa.runlog.store.RunRecorder

/** What the usage screen reads. */
interface UsageDependencies {
    val recorder: RunRecorder
}
