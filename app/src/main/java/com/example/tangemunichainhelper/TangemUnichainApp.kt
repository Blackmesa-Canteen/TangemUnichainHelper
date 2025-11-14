package com.example.tangemunichainhelper

import android.app.Application
import timber.log.Timber

class TangemUnichainApp : Application() {
    override fun onCreate() {
        super.onCreate()

        Timber.plant(Timber.DebugTree())

        Timber.d("Tangem Unichain App initialized!!")
    }
}