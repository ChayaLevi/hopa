package com.hopa.app.di

import com.hopa.feature.capabilities.CapabilitiesDependencies
import com.hopa.feature.chat.ChatDependencies
import com.hopa.feature.conversations.ConversationsDependencies
import com.hopa.feature.easy.EasyDependencies
import com.hopa.feature.execution.ExecutionDependencies
import com.hopa.feature.history.ActivityDependencies
import com.hopa.feature.history.HistoryDependencies
import com.hopa.feature.history.RecipesDependencies
import com.hopa.feature.launchpad.LaunchpadDependencies
import com.hopa.feature.memory.MemoryDependencies
import com.hopa.feature.power.PowerDependencies
import com.hopa.feature.profiles.ProfilesDependencies
import com.hopa.feature.runs.RunsDependencies
import com.hopa.feature.settings.PairingDependencies
import com.hopa.feature.settings.SettingsDependencies
import com.hopa.feature.settings.SurfaceDependencies
import com.hopa.feature.skills.SkillsDependencies
import com.hopa.feature.usage.UsageDependencies
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** The app container is the single implementation behind every feature interface. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ContainerBindings {
    @Binds
    @Singleton
    abstract fun chat(container: AppContainer): ChatDependencies

    @Binds
    @Singleton
    abstract fun conversations(container: AppContainer): ConversationsDependencies

    @Binds
    @Singleton
    abstract fun activity(container: AppContainer): ActivityDependencies

    @Binds
    @Singleton
    abstract fun history(container: AppContainer): HistoryDependencies

    @Binds
    @Singleton
    abstract fun recipes(container: AppContainer): RecipesDependencies

    @Binds
    @Singleton
    abstract fun runs(container: AppContainer): RunsDependencies

    @Binds
    @Singleton
    abstract fun memory(container: AppContainer): MemoryDependencies

    @Binds
    @Singleton
    abstract fun skills(container: AppContainer): SkillsDependencies

    @Binds
    @Singleton
    abstract fun usage(container: AppContainer): UsageDependencies

    @Binds
    @Singleton
    abstract fun settings(container: AppContainer): SettingsDependencies

    @Binds
    @Singleton
    abstract fun pairing(container: AppContainer): PairingDependencies

    @Binds
    @Singleton
    abstract fun surface(container: AppContainer): SurfaceDependencies

    @Binds
    @Singleton
    abstract fun profiles(container: AppContainer): ProfilesDependencies

    @Binds
    @Singleton
    abstract fun easy(container: AppContainer): EasyDependencies

    @Binds
    @Singleton
    abstract fun power(container: AppContainer): PowerDependencies

    @Binds
    @Singleton
    abstract fun launchpad(container: AppContainer): LaunchpadDependencies

    @Binds
    @Singleton
    abstract fun capabilities(container: AppContainer): CapabilitiesDependencies

    @Binds
    @Singleton
    abstract fun execution(container: AppContainer): ExecutionDependencies

    @Binds
    @Singleton
    abstract fun localModels(container: AppContainer): LocalModelHost
}
