package com.hopa.app.di

import com.hopa.runlog.store.RunRecorder

/** What the usage screen reads. */
interface UsageDependencies {
    val recorder: RunRecorder
}
