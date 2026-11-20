package com.hopa.feature.power

import com.hopa.runlog.store.RunRecorder

/** What the Power home reads. */
interface PowerDependencies {
    val recorder: RunRecorder
}
