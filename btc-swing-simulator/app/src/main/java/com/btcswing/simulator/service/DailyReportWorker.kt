package com.btcswing.simulator.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.btcswing.simulator.di.ServiceLocator
import com.btcswing.simulator.widget.BtcWidgetProvider
import java.util.Locale

/**
 * Worker periódico (24h) que gera o relatório diário no horário escolhido
 * pelo usuário e o exibe como notificação. Mecanismo oficial (WorkManager):
 * sobrevive a reinicialização e a processos mortos; se o aparelho estiver
 * dormindo no minuto exato, o sistema executa assim que possível — o
 * relatório reflete os dados reais persistidos, nunca operações inventadas.
 */
class DailyReportWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        val now = System.currentTimeMillis()
        val report = ServiceLocator.tradingEngine.generateDailyReport(now)
        val title = String.format(
            Locale.getDefault(), "Relatório diário — %+.2f USDT (%+.2f%%)",
            report.realizedPnlUsdt, report.realizedPnlPct
        )
        NotificationHelper.report(applicationContext, title, report.summaryLines)
        BtcWidgetProvider.refreshAll(applicationContext)
        Result.success()
    } catch (t: Throwable) {
        if (runAttemptCount < 3) Result.retry() else Result.failure()
    }
}
