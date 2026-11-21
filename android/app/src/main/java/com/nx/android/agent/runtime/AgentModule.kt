package com.nx.android.agent.runtime

import com.nx.android.agent.AgentChat
import com.nx.android.agent.AgentSettings
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class AgentModule {
    @Binds abstract fun bindChat(impl: AgentChatImpl): AgentChat
    @Binds abstract fun bindSettings(impl: AgentSettingsStore): AgentSettings
}
