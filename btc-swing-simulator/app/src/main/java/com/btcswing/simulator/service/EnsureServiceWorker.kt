package com.btcswing.simulator.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.btcswing.simulator.di.ServiceLocator

/**
 * Watchdog oficial via WorkManager (a cada ~15 min): se o robô está habilitado
 * e o serviço em primeiro plano não está rodando (processo morto pelo sistema,
 * limite de dataSync do Android 15 etc.), religa o serviço. Isso garante
 * funcionamento contínuo SEM burlar nenhuma segurança do Android.
 */
class EnsureServiceWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        val settings = ServiceLocator.settings.current()
        if (settings.botEnabled && !TradingForegroundService.isRunning) {
            TradingForegroundService.start(applicationContext)
        }
        Result.success()
    } catch (_: Throwable) {
        Result.retry()
    }
}
