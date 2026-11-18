package com.btcswing.simulator.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import com.btcswing.simulator.R
import com.btcswing.simulator.di.ServiceLocator
import com.btcswing.simulator.widget.BtcWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Serviço em PRIMEIRO PLANO (mecanismo oficial do Android) que mantém o robô
 * analisando o mercado continuamente, com notificação persistente.
 *
 * - Nenhum modo próprio de economia de bateria: usa apenas APIs oficiais
 *   (foreground service dataSync + wake lock curto por ciclo + WorkManager).
 * - Sobrevive a reinicialização via BootReceiver e ao processo ser morto via
 *   EnsureServiceWorker (watchdog periódico do WorkManager).
 * - Interrupções não geram operações inventadas: cada ciclo só age com
 *   candles reais buscados na hora (ou cache explícito, sinalizado no log).
 */
class TradingForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loopJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        startInForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()
        if (loopJob?.isActive != true) startLoop()
        // Se o sistema matar o serviço, ele é recriado automaticamente.
        return START_STICKY
    }

    private fun startInForeground() {
        val notification = NotificationHelper.foreground(
            this, getString(R.string.notif_foreground_text)
        )
        ServiceCompat.startForeground(
            this, NotificationHelper.ID_FOREGROUND, notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    private fun startLoop() {
        loopJob = scope.launch {
            // Ciclo imediato ao iniciar e depois a cada intervalo configurado
            while (isActive) {
                val intervalMinutes = runCycleSafely()
                delay(intervalMinutes * 60_000L)
            }
        }
    }

    /** Roda um ciclo com wake lock curto; retorna o intervalo (min) a esperar. */
    private suspend fun runCycleSafely(): Int {
        var interval = 15
        acquireWakeLock()
        try {
            val outcome = withContext(Dispatchers.IO) {
                ServiceLocator.tradingEngine.runCycle()
            }
            interval = outcome.settings.analysisIntervalMinutes
            if (!outcome.settings.botEnabled) {
                // Bot desligado pela UI: mantém serviço parado
                stopSelf()
                return interval
            }
            updateForegroundNotification(outcome.message)
            outcome.executedTrades.forEach { NotificationHelper.tradeAlert(this, it) }
            BtcWidgetProvider.refreshAll(this)
        } catch (t: Throwable) {
            updateForegroundNotification("Erro no ciclo: ${t.message}")
        } finally {
            releaseWakeLock()
        }
        return interval
    }

    private fun updateForegroundNotification(status: String) {
        val nm = getSystemService(android.app.NotificationManager::class.java) ?: return
        nm.notify(NotificationHelper.ID_FOREGROUND, NotificationHelper.foreground(this, status))
    }

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(PowerManager::class.java) ?: return
            if (wakeLock?.isHeld == true) return
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "btcswing:cycle").apply {
                setReferenceCounted(false)
                acquire(60_000L) // teto de segurança de 60s por ciclo
            }
        } catch (_: Exception) { }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) { }
    }

    override fun onDestroy() {
        isRunning = false
        loopJob?.cancel()
        scope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }

    companion object {
        @Volatile
        var isRunning: Boolean = false
            private set

        fun start(context: android.content.Context) {
            val i = Intent(context, TradingForegroundService::class.java)
            androidx.core.content.ContextCompat.startForegroundService(context, i)
        }

        fun stop(context: android.content.Context) {
            context.stopService(Intent(context, TradingForegroundService::class.java))
        }
    }
}
