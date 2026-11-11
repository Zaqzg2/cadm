package com.cady.cadymarketing

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Nothing runs here on purpose: Firebase initialises itself, and Room / DataStore are built lazily on
 * first use, so cold start stays short.
 */
@HiltAndroidApp
class CadyMarketingApp : Application()
