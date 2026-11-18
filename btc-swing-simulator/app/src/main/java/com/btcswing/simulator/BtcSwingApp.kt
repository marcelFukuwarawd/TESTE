package com.btcswing.simulator

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.btcswing.simulator.di.ServiceLocator

class BtcSwingApp : Application() {

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        createChannels()
    }

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        val channels = listOf(
            NotificationChannel(CH_TRADING, getString(R.string.notif_channel_trading), NotificationManager.IMPORTANCE_LOW)
                .apply { description = "Notificação persistente do robô em funcionamento" },
            NotificationChannel(CH_ALERTS, getString(R.string.notif_channel_alerts), NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Compras e vendas simuladas executadas" },
            NotificationChannel(CH_REPORT, getString(R.string.notif_channel_report), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Relatório diário de desempenho" }
        )
        channels.forEach { nm.createNotificationChannel(it) }
    }

    companion object {
        const val CH_TRADING = "trading"
        const val CH_ALERTS = "alerts"
        const val CH_REPORT = "report"
    }
}
