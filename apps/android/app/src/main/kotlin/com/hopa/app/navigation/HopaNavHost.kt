package com.hopa.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.hopa.app.di.AppContainer
import com.hopa.feature.browser.BrowserRoute
import com.hopa.feature.execution.ComputerRoute
import com.hopa.feature.execution.HowHopaActsRoute
import com.hopa.feature.execution.RunPrefsRoute
import com.hopa.feature.execution.RunSetupRoute
import com.hopa.feature.execution.RunningAppsRoute
import com.hopa.feature.history.HistoryRoute
import com.hopa.feature.history.RecipesRoute
import com.hopa.feature.launchpad.AppCardRoute
import com.hopa.feature.launchpad.LaunchpadRoute
import com.hopa.feature.memory.MemoryRoute
import com.hopa.feature.profiles.ProfilesRoute
import com.hopa.feature.runs.RunsRoute
import com.hopa.app.feature.settings.AboutScreen
import com.hopa.app.feature.settings.AccessibilityRoute
import com.hopa.app.feature.settings.SelfCheckRoute
import com.hopa.app.feature.settings.SettingsRoute
import com.hopa.app.feature.shell.HomeShell
import com.hopa.feature.skills.SkillsRoute
import com.hopa.feature.usage.UsageRoute

@Composable
fun HopaNavHost(container: AppContainer, initialGoal: String = "") {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = HopaDestination.Chat) {
        composable<HopaDestination.Chat> {
            HomeShell(
                container = container,
                onOpenSettings = { navController.navigate(HopaDestination.Settings) },
                onOpenRuns = { navController.navigate(HopaDestination.Runs) },
                onOpenUsage = { navController.navigate(HopaDestination.Usage) },
                onOpenHistory = { navController.navigate(HopaDestination.History) },
                onOpenRecipes = { navController.navigate(HopaDestination.Recipes) },
                onOpenMemory = { navController.navigate(HopaDestination.Memory) },
                onOpenSkills = { navController.navigate(HopaDestination.Skills) },
                onOpenRunningApps = { navController.navigate(HopaDestination.RunningApps) },
                onOpenActMode = { navController.navigate(HopaDestination.ActMode) },
                onOpenBrowser = { navController.navigate(HopaDestination.Browser()) },
                initialGoal = initialGoal,
            )
        }
        composable<HopaDestination.Usage> {
            UsageRoute(
                container = container,
                onBack = { navController.popBackStack() },
            )
        }
        composable<HopaDestination.History> {
            HistoryRoute(
                container = container,
                onBack = { navController.popBackStack() },
            )
        }
        composable<HopaDestination.Recipes> {
            RecipesRoute(
                container = container,
                onBack = { navController.popBackStack() },
            )
        }
        composable<HopaDestination.Memory> {
            MemoryRoute(
                container = container,
                onBack = { navController.popBackStack() },
            )
        }
        composable<HopaDestination.Skills> {
            SkillsRoute(
                container = container,
                onBack = { navController.popBackStack() },
            )
        }
        composable<HopaDestination.Browser> { entry ->
            BrowserRoute(
                initialUrl = entry.toRoute<HopaDestination.Browser>().url,
                onBack = { navController.popBackStack() },
            )
        }
        composable<HopaDestination.Settings> {
            SettingsRoute(
                container = container,
                onBack = { navController.popBackStack() },
                onOpenProfiles = { navController.navigate(HopaDestination.Profiles) },
                onOpenSelfCheck = { navController.navigate(HopaDestination.SelfCheck) },
                onOpenAbout = { navController.navigate(HopaDestination.About) },
            )
        }
        composable<HopaDestination.RunningApps> {
            RunningAppsRoute(
                container = container,
                onBack = { navController.popBackStack() },
                onOpenHowHopaActs = { navController.navigate(HopaDestination.ActMode) },
                onOpenAllApps = { navController.navigate(HopaDestination.Launchpad("apps")) },
                onOpenSites = { navController.navigate(HopaDestination.Launchpad("sites")) },
                onOpenSetup = { navController.navigate(HopaDestination.RunSetup) },
                onOpenRunPrefs = { navController.navigate(HopaDestination.RunPrefs) },
            )
        }
        composable<HopaDestination.ActMode> {
            HowHopaActsRoute(
                container = container,
                onBack = { navController.popBackStack() },
                onOpenScreenAccess = { navController.navigate(HopaDestination.Accessibility) },
                onOpenComputer = { navController.navigate(HopaDestination.Computer) },
                onOpenSetup = { navController.navigate(HopaDestination.RunSetup) },
            )
        }
        composable<HopaDestination.RunSetup> {
            RunSetupRoute(
                container = container,
                onBack = { navController.popBackStack() },
                onOpenScreenAccess = { navController.navigate(HopaDestination.Accessibility) },
                onOpenComputer = { navController.navigate(HopaDestination.Computer) },
            )
        }
        composable<HopaDestination.RunPrefs> {
            RunPrefsRoute(
                container = container,
                onBack = { navController.popBackStack() },
            )
        }
        composable<HopaDestination.Computer> {
            ComputerRoute(
                container = container,
                onBack = { navController.popBackStack() },
            )
        }
        composable<HopaDestination.SelfCheck> {
            SelfCheckRoute(
                container = container,
                onBack = { navController.popBackStack() },
            )
        }
        composable<HopaDestination.About> {
            AboutScreen(onBack = { navController.popBackStack() })
        }
        composable<HopaDestination.Accessibility> {
            AccessibilityRoute(
                container = container,
                onBack = { navController.popBackStack() },
            )
        }
        composable<HopaDestination.Profiles> {
            ProfilesRoute(
                container = container,
                onBack = { navController.popBackStack() },
            )
        }
        composable<HopaDestination.Runs> {
            RunsRoute(
                container = container,
                onBack = { navController.popBackStack() },
            )
        }
        composable<HopaDestination.Launchpad> { entry ->
            LaunchpadRoute(
                container = container,
                onBack = { navController.popBackStack() },
                section = entry.toRoute<HopaDestination.Launchpad>().section,
                onOpenApp = { name ->
                    navController.navigate(HopaDestination.AppCard(name))
                },
                onOpenSite = { url ->
                    navController.navigate(HopaDestination.Browser(url))
                },
            )
        }
        composable<HopaDestination.AppCard> { entry ->
            AppCardRoute(
                container = container,
                name = entry.toRoute<HopaDestination.AppCard>().name,
                onBack = { navController.popBackStack() },
                onOpenScreenAccess = { navController.navigate(HopaDestination.Accessibility) },
            )
        }
    }
}
