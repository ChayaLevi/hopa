package com.hopa.feature.profiles

import com.hopa.stores.SettingsRepository

/** What the profiles page reads and writes. */
interface ProfilesDependencies {
    val settings: SettingsRepository
}
