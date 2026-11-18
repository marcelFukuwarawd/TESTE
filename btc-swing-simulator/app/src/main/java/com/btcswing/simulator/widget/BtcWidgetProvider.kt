package com.btcswing.simulator.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import com.btcswing.simulator.MainActivity
import com.btcswing.simulator.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Widget grande e redimensionável (padrão 6x4, suporta até 6x6+).
 *
 * - Exibe saldo, preço, P/L, posição, última decisão e últimas operações.
 * - Quanto MAIOR o espaço concedido pelo launcher, MAIS detalhes aparecem
 *   (lista de trades só em widgets altos).
 * - Atualização periódica pelo sistema (updatePeriodMillis=30min), a cada
 *   ciclo do robô (refreshAll) e no boot.
 * - Clique abre o app.
 */
class BtcWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val state = WidgetState.load(context)
                for (id in appWidgetIds) {
                    val views = buildViews(context, appWidgetManager, id, state)
                    appWidgetManager.updateAppWidget(id, views)
                }
            } catch (_: Exception) {
            } finally {
                pending.finish()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle?
    ) {
        // Redimensionou → re-renderiza com o nível de detalhe adequado
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val state = WidgetState.load(context)
                appWidgetManager.updateAppWidget(
                    appWidgetId, buildViews(context, appWidgetManager, appWidgetId, state)
                )
            } catch (_: Exception) {
            } finally {
                pending.finish()
            }
        }
    }

    companion object {

        /** Atualiza todos os widgets do launcher (chamado após cada ciclo/relatório). */
        fun refreshAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context) ?: return
            val ids = mgr.getAppWidgetIds(ComponentName(context, BtcWidgetProvider::class.java))
            if (ids.isEmpty()) return
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val state = WidgetState.load(context)
                    for (id in ids) {
                        try {
                            mgr.updateAppWidget(id, buildViews(context, mgr, id, state))
                        } catch (_: Exception) { }
                    }
                } catch (_: Exception) { }
            }
        }

        internal fun buildViews(
            context: Context,
            mgr: AppWidgetManager,
            widgetId: Int,
            state: WidgetState
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_btc)

            // Altura disponível → nível de detalhe (dp)
            val options = mgr.getAppWidgetOptions(widgetId)
            val maxHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 180)
            val showDetails = maxHeightDp >= 250

            views.setTextViewText(
                R.id.widget_status,
                if (state.botActive) "● ativo" else "○ parado"
            )
            views.setTextColor(
                R.id.widget_status,
                if (state.botActive) Color.parseColor("#4ADE80") else Color.parseColor("#94A3B8")
            )

            views.setTextViewText(
                R.id.widget_price,
                if (state.price > 0) String.format(Locale.US, "$%,.0f", state.price) else "$ --"
            )

            views.setTextViewText(
                R.id.widget_balance,
                String.format(Locale.US, "Saldo: %,.2f USDT", state.cashUsdt)
            )

            val pnlText = String.format(Locale.US, "P/L: %,.2f USDT (%+.2f%%)", state.pnlUsdt, state.pnlPct)
            views.setTextViewText(R.id.widget_pnl, pnlText)
            views.setTextColor(
                R.id.widget_pnl,
                when {
                    state.pnlUsdt > 0 -> Color.parseColor("#4ADE80")
                    state.pnlUsdt < 0 -> Color.parseColor("#F87171")
                    else -> Color.WHITE
                }
            )

            views.setTextViewText(R.id.widget_position, state.positionLine)
            views.setTextViewText(R.id.widget_last_decision, state.lastDecisionLine)

            if (showDetails && state.lastTradesLines.isNotEmpty()) {
                views.setViewVisibility(R.id.widget_last_trades, View.VISIBLE)
                views.setTextViewText(
                    R.id.widget_last_trades,
                    state.lastTradesLines.take(5).joinToString("\n")
                )
            } else {
                views.setViewVisibility(R.id.widget_last_trades, View.GONE)
            }

            views.setTextViewText(R.id.widget_updated, state.updatedAtLine)

            // Clique → abre o app
            val intent = Intent(context, MainActivity::class.java)
            val pi = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pi)
            return views
        }
    }
}
