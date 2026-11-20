package com.hopa.feature.launchpad

import android.content.Context
import com.hopa.capabilities.display.client.ShellClient
import com.hopa.capabilities.system.AndroidPackageCatalog
import com.hopa.catalog.store.CatalogSnapshot
import com.hopa.stores.SettingsRepository

/** What the launchpad and the app card need. */
interface LaunchpadDependencies {
    val context: Context
    val settings: SettingsRepository
    val packages: AndroidPackageCatalog
    val knowledge: CatalogSnapshot
    val shell: ShellClient
}
