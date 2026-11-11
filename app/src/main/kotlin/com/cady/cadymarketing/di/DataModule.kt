package com.cady.cadymarketing.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.cady.cadymarketing.data.local.CadyMarketingDatabase
import com.cady.cadymarketing.data.local.SyncMetaDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun providePreferencesDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.settingsDataStore

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): CadyMarketingDatabase =
        Room.databaseBuilder(context, CadyMarketingDatabase::class.java, CadyMarketingDatabase.NAME).build()

    @Provides
    fun provideSyncMetaDao(database: CadyMarketingDatabase): SyncMetaDao = database.syncMetaDao()
}
