package com.example.tangemunichainhelper

import android.app.Application
import com.tangem.sdk.BuildConfig
import timber.log.Timber

class TangemUnichainApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Initialize Timber for logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        Timber.d("Tangem Unichain App initialized")
    }
}