package com.hopa.feature.history

import com.hopa.data.store.DbRunHistory

/** What the history page reads. */
interface HistoryDependencies {
    val runHistory: DbRunHistory
}
