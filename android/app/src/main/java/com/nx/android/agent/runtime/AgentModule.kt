package com.nx.android.agent.runtime

import com.nx.android.agent.AgentChat
import com.nx.android.agent.AgentSettings
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Binds the assistant's contract to its runtime: the feature layer sees [AgentChat] and [AgentSettings],
 * the AgentScope implementation and the prefs-backed settings stay behind them.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AgentModule {

    @Binds
    abstract fun bindChat(impl: AgentChatImpl): AgentChat

    @Binds
    abstract fun bindSettings(impl: AgentSettingsStore): AgentSettings
}
