package com.nx.android.agent.runtime

import com.nx.android.agent.AgentChat
import com.nx.android.agent.AgentSettings
import com.nx.android.agent.env.PiAgentChat
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** The panel's chat is the env agent (ADR-009); the settings are the store. */
@Module
@InstallIn(SingletonComponent::class)
abstract class AgentModule {
    @Binds abstract fun bindChat(impl: PiAgentChat): AgentChat
    @Binds abstract fun bindSettings(impl: AgentSettingsStore): AgentSettings
}
