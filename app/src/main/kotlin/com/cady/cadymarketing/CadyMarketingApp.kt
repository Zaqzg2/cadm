package com.cady.cadymarketing

import android.app.Application
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp

/**
 * Nothing runs here on purpose: Firebase initialises itself, and Room / DataStore / WorkManager are built lazily on
 * first use, so cold start stays short. WorkManager's own automatic start-up is switched off in the manifest;
 * this is the configuration it uses when it is first asked for.
 */
@HiltAndroidApp
class CadyMarketingApp : Application(), Configuration.Provider {
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()
}
