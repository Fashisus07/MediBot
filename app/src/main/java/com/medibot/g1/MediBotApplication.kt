package com.medibot.g1

import android.app.Application
import com.medibot.g1.data.local.AppDatabase

class MediBotApplication : Application() {
    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }

    override fun onCreate() {
        super.onCreate()
    }
}

