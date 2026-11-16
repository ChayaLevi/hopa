package com.hopa.capabilities.system

import android.content.Context
import android.content.Intent
import com.hopa.core.ports.InstalledApp
import com.hopa.core.ports.PackageCatalog

/**
 * The apps this phone can open. Only launcher entries are listed: an app without
 * one cannot be started by [launch], and listing it would invite a dead end.
 */
class AndroidPackageCatalog(
    private val context: Context,
) : PackageCatalog {
    override suspend fun installed(): List<InstalledApp> {
        val manager = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return manager.queryIntentActivities(launcher, 0)
            .asSequence()
            .filter { it.activityInfo.packageName != context.packageName }
            .map {
                InstalledApp(
                    packageName = it.activityInfo.packageName,
                    label = it.loadLabel(manager).toString(),
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    override suspend fun launch(packageName: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: throw IllegalArgumentException("no launch intent for $packageName")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
