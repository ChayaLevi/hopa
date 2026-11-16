package com.hopa.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.hopa.app.di.AppContainer
import com.hopa.app.feature.launchpad.AppCardRoute
import com.hopa.app.feature.launchpad.LaunchpadRoute
import com.hopa.app.feature.memory.MemoryRoute
import com.hopa.app.feature.runningapps.ComputerRoute
import com.hopa.app.feature.runningapps.RunningAppsRoute
import com.hopa.app.feature.browser.BrowserRoute
import com.hopa.app.feature.profiles.ProfilesRoute
import com.hopa.app.feature.runs.RunsRoute
import com.hopa.app.feature.settings.AboutScreen
import com.hopa.app.feature.settings.AccessibilityRoute
import com.hopa.app.feature.settings.SelfCheckRoute
import com.hopa.app.feature.settings.SettingsRoute
import com.hopa.app.feature.usage.UsageRoute
import com.hopa.app.feature.shell.HomeShell

@Composable
fun HopaNavHost(container: AppContainer, initialGoal: String = "") {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = HopaDestination.CHAT) {
        composable(HopaDestination.CHAT) {
            HomeShell(
                container = container,
                onOpenSettings = { navController.navigate(HopaDestination.SETTINGS) },
                onOpenRuns = { navController.navigate(HopaDestination.RUNS) },
                onOpenUsage = { navController.navigate(HopaDestination.USAGE) },
                onOpenMemory = { navController.navigate(HopaDestination.MEMORY) },
                onOpenRunningApps = { navController.navigate(HopaDestination.RUNNING_APPS) },
                onOpenBrowser = {
                    navController.navigate(HopaDestination.BROWSER + "?url=")
                },
                initialGoal = initialGoal,
            )
        }
        composable(HopaDestination.USAGE) {
            UsageRoute(
                container = container,
                onBack = { navController.popBackStack() },
            )
        }
        composable(HopaDestination.MEMORY) {
            MemoryRoute(
                container = container,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = HopaDestination.BROWSER + "?url={url}",
            arguments = listOf(
                navArgument("url") {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) { entry ->
            BrowserRoute(
                initialUrl = entry.arguments?.getString("url").orEmpty(),
                onBack = { navController.popBackStack() },
            )
        }
        composable(HopaDestination.SETTINGS) {
            SettingsRoute(
                container = container,
                onBack = { navController.popBackStack() },
                onOpenCapabilities = { navController.popBackStack() },
                onOpenProfiles = { navController.navigate(HopaDestination.PROFILES) },
                onOpenSelfCheck = { navController.navigate(HopaDestination.SELF_CHECK) },
                onOpenAbout = { navController.navigate(HopaDestination.ABOUT) },
            )
        }
        composable(HopaDestination.RUNNING_APPS) {
            RunningAppsRoute(
                container = container,
                onBack = { navController.popBackStack() },
                onOpenLaunchpad = { navController.navigate(HopaDestination.LAUNCHPAD) },
                onOpenScreenAccess = { navController.navigate(HopaDestination.ACCESSIBILITY) },
                onOpenComputer = { navController.navigate(HopaDestination.COMPUTER) },
            )
        }
        composable(HopaDestination.COMPUTER) {
            ComputerRoute(
                container = container,
                onBack = { navController.popBackStack() },
            )
        }
        composable(HopaDestination.SELF_CHECK) {
            SelfCheckRoute(
                container = container,
                onBack = { navController.popBackStack() },
            )
        }
        composable(HopaDestination.ABOUT) {
            AboutScreen(onBack = { navController.popBackStack() })
        }
        composable(HopaDestination.ACCESSIBILITY) {
            AccessibilityRoute(
                container = container,
                onBack = { navController.popBackStack() },
            )
        }
        composable(HopaDestination.PROFILES) {
            ProfilesRoute(
                container = container,
                onBack = { navController.popBackStack() },
            )
        }
        composable(HopaDestination.RUNS) {
            RunsRoute(
                container = container,
                onBack = { navController.popBackStack() },
            )
        }
        composable(HopaDestination.LAUNCHPAD) {
            LaunchpadRoute(
                container = container,
                onBack = { navController.popBackStack() },
                onOpenApp = { name ->
                    navController.navigate(
                        HopaDestination.APP_CARD + "/" + android.net.Uri.encode(name),
                    )
                },
                onOpenSite = { url ->
                    navController.navigate(
                        HopaDestination.BROWSER + "?url=" + android.net.Uri.encode(url),
                    )
                },
            )
        }
        composable(
            route = HopaDestination.APP_CARD + "/{name}",
            arguments = listOf(
                navArgument("name") { type = NavType.StringType },
            ),
        ) { entry ->
            AppCardRoute(
                container = container,
                name = entry.arguments?.getString("name").orEmpty(),
                onBack = { navController.popBackStack() },
                onOpenScreenAccess = { navController.navigate(HopaDestination.ACCESSIBILITY) },
            )
        }
    }
}
