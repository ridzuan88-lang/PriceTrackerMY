package com.chewanninja.pricetrackermy

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.chewanninja.pricetrackermy.data.AppDatabase

class PriceApp : Application() {
    val db by lazy { AppDatabase.get(this) }
    val repo by lazy { Repository(this, db) }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(NotificationChannel("budget", "Budget alerts", NotificationManager.IMPORTANCE_DEFAULT))
        }
        SyncWorker.schedulePeriodic(this)
    }
}
