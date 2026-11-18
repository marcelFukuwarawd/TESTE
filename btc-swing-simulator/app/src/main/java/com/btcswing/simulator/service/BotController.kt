package com.btcswing.simulator.service

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.btcswing.simulator.di.ServiceLocator
import com.btcswing.simulator.domain.model.BotSettings
import java.util.Calendar
import java.util.concurrent.TimeUnit

/** Liga/desliga o robô e agenda os workers oficiais (relatório + watchdog). */
object BotController {

    private const val WORK_REPORT = "daily-report"
    private const val WORK_WATCHDOG = "ensure-service"

    /** Liga o robô: persiste, agenda workers e sobe o serviço em primeiro plano. */
    suspend fun enable(context: Context) {
        ServiceLocator.settings.setBotEnabled(true)
        val settings = ServiceLocator.settings.current()
        scheduleWorkers(context, settings)
        TradingForegroundService.start(context)
    }

    /** Desliga o robô: para o serviço; workers permanecem (watchdog não religa). */
    suspend fun disable(context: Context) {
        ServiceLocator.settings.setBotEnabled(false)
        TradingForegroundService.stop(context)
    }

    /** Reagenda o relatório diário (usar quando o usuário muda o horário). */
    fun scheduleWorkers(context: Context, settings: BotSettings) {
        val wm = WorkManager.getInstance(context)

        // Relatório diário: primeira execução na próxima ocorrência do horário
        // escolhido; depois, a cada 24h.
        val delayToReport = millisUntilNextHour(settings.dailyReportHour)
        val reportRequest = PeriodicWorkRequestBuilder<DailyReportWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delayToReport, TimeUnit.MILLISECONDS)
            .build()
        wm.enqueueUniquePeriodicWork(
            WORK_REPORT, ExistingPeriodicWorkPolicy.UPDATE, reportRequest
        )

        // Watchdog do serviço em primeiro plano (mínimo oficial: 15 min)
        val watchdog = PeriodicWorkRequestBuilder<EnsureServiceWorker>(15, TimeUnit.MINUTES)
            .build()
        wm.enqueueUniquePeriodicWork(
            WORK_WATCHDOG, ExistingPeriodicWorkPolicy.KEEP, watchdog
        )
    }

    /** Milissegundos até a próxima ocorrência de `hour` no fuso local. */
    fun millisUntilNextHour(hour: Int, now: Calendar = Calendar.getInstance()): Long {
        val next = now.clone() as Calendar
        next.set(Calendar.HOUR_OF_DAY, hour)
        next.set(Calendar.MINUTE, 0)
        next.set(Calendar.SECOND, 0)
        next.set(Calendar.MILLISECOND, 0)
        if (!next.after(now)) next.add(Calendar.DAY_OF_YEAR, 1)
        return next.timeInMillis - now.timeInMillis
    }
}
