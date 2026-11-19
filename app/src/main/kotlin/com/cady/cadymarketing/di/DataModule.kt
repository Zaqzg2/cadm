package com.cady.cadymarketing.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.cady.cadymarketing.data.local.AudienceDao
import com.cady.cadymarketing.data.local.CadyMarketingDatabase
import com.cady.cadymarketing.data.local.CampaignDao
import com.cady.cadymarketing.data.local.CatalogDao
import com.cady.cadymarketing.data.local.ContentDao
import com.cady.cadymarketing.data.local.DocDao
import com.cady.cadymarketing.data.local.OccasionDao
import com.cady.cadymarketing.data.local.ReportDao
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
        Room.databaseBuilder(context, CadyMarketingDatabase::class.java, CadyMarketingDatabase.NAME)
            .addMigrations(
                CadyMarketingDatabase.MIGRATION_1_2,
                CadyMarketingDatabase.MIGRATION_2_3,
                CadyMarketingDatabase.MIGRATION_3_4,
                CadyMarketingDatabase.MIGRATION_4_5,
            )
            .build()

    @Provides
    fun provideSyncMetaDao(database: CadyMarketingDatabase): SyncMetaDao = database.syncMetaDao()

    @Provides
    fun provideContentDao(database: CadyMarketingDatabase): ContentDao = database.contentDao()

    @Provides
    fun provideCatalogDao(database: CadyMarketingDatabase): CatalogDao = database.catalogDao()

    @Provides
    fun provideDocDao(database: CadyMarketingDatabase): DocDao = database.docDao()

    @Provides
    fun provideCampaignDao(database: CadyMarketingDatabase): CampaignDao = database.campaignDao()

    @Provides
    fun provideOccasionDao(database: CadyMarketingDatabase): OccasionDao = database.occasionDao()

    @Provides
    fun provideAudienceDao(database: CadyMarketingDatabase): AudienceDao = database.audienceDao()

    @Provides
    fun provideReportDao(database: CadyMarketingDatabase): ReportDao = database.reportDao()
}
