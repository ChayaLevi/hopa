package com.hopa.feature.runs

import com.hopa.runlog.store.RunRecorder

/** What the runs page reads and clears. */
interface RunsDependencies {
    val recorder: RunRecorder
}
