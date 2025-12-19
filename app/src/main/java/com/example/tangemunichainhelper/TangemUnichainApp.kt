package com.example.tangemunichainhelper

import android.app.Application
import timber.log.Timber

class TangemUnichainApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Only enable debug logging in debug builds
        // This prevents sensitive transaction data from being logged in production
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
            Timber.d("Tangem Unichain App initialized (DEBUG mode)")
        }
    }
}