package com.btcswing.simulator.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.btcswing.simulator.BtcSwingApp
import com.btcswing.simulator.MainActivity
import com.btcswing.simulator.R
import com.btcswing.simulator.domain.simulation.Side
import com.btcswing.simulator.domain.simulation.TradeRecord
import java.util.Locale

/** Notificações: persistente do serviço, alertas de operação e relatório diário. */
object NotificationHelper {

    const val ID_FOREGROUND = 1001
    const val ID_TRADE_ALERT = 2001
    const val ID_REPORT = 3001

    private fun contentIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    /** Notificação persistente do serviço em primeiro plano. */
    fun foreground(context: Context, statusLine: String): Notification =
        NotificationCompat.Builder(context, BtcSwingApp.CH_TRADING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_foreground_title))
            .setContentText(statusLine)
            .setStyle(NotificationCompat.BigTextStyle().bigText(statusLine))
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(contentIntent(context))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

    /** Alerta de compra/venda simulada executada. */
    fun tradeAlert(context: Context, trade: TradeRecord) {
        val emoji = if (trade.side == Side.BUY) "🟢 COMPRA" else "🔴 VENDA"
        val pnl = trade.realizedPnlUsdt?.let {
            String.format(Locale.US, " | PnL: %+.2f USDT (%+.2f%%)", it, trade.realizedPnlPct ?: 0.0)
        } ?: ""
        val text = String.format(
            Locale.US, "%s de %.6f BTC a $%,.2f%s\n%s",
            emoji, trade.qtyBtc, trade.execPrice, pnl, trade.reason
        )
        val n = NotificationCompat.Builder(context, BtcSwingApp.CH_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("BTC Swing — operação simulada")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(contentIntent(context))
            .build()
        context.getSystemService(NotificationManager::class.java)
            ?.notify(ID_TRADE_ALERT + (trade.time % 1000).toInt(), n)
    }

    /** Notificação do relatório diário. */
    fun report(context: Context, title: String, lines: List<String>) {
        val text = lines.joinToString("\n")
        val n = NotificationCompat.Builder(context, BtcSwingApp.CH_REPORT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(lines.take(2).joinToString(" • "))
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(contentIntent(context))
            .build()
        context.getSystemService(NotificationManager::class.java)?.notify(ID_REPORT, n)
    }
}
