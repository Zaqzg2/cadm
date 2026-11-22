package com.cady.cadymarketing.data.notify

import com.cady.cadymarketing.data.content.ContentRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** How the two workers reach the app's objects: a worker is not made by Hilt, so it asks for them here. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface BackgroundEntryPoint {
    fun contentRepository(): ContentRepository

    fun notifier(): Notifier

    fun pendingCheck(): PendingCheck
}
