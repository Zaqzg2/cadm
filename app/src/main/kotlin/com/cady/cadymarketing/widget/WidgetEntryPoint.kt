package com.cady.cadymarketing.widget

import com.cady.cadymarketing.data.campaign.CampaignRepository
import com.cady.cadymarketing.data.content.ContentRepository
import com.cady.cadymarketing.data.settings.SettingsStore
import com.cady.cadymarketing.data.team.RoleRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** How the home-screen widget reaches the app's data: a broadcast receiver is not made by Hilt, so it asks for them here. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun contentRepository(): ContentRepository

    fun campaignRepository(): CampaignRepository

    fun roleRepository(): RoleRepository

    fun settingsStore(): SettingsStore
}
