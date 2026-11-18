package com.btcswing.simulator.widget

import android.content.Context
import com.btcswing.simulator.data.db.toDomain
import com.btcswing.simulator.di.ServiceLocator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Estado consolidado exibido no widget (lido do banco local — offline-first). */
data class WidgetState(
    val botActive: Boolean = false,
    val price: Double = 0.0,
    val cashUsdt: Double = 0.0,
    val equityUsdt: Double = 0.0,
    val initialCapitalUsdt: Double = 0.0,
    val pnlUsdt: Double = 0.0,
    val pnlPct: Double = 0.0,
    val positionLine: String = "Posição: nenhuma",
    val lastDecisionLine: String = "Última decisão: aguardando análise…",
    val lastTradesLines: List<String> = emptyList(),
    val updatedAtLine: String = "Atualizado: --"
) {
    companion object {
        private val timeFmt = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())

        /** Monta o estado a partir dos dados persistidos (rápido e local). */
        suspend fun load(context: Context): WidgetState {
            val db = ServiceLocator.database
            val settings = ServiceLocator.settings.current()
            val row = db.portfolioDao().get()
            val portfolio = row?.toDomain()
            val price = row?.lastPrice ?: 0.0
            val equity = portfolio?.equity(price) ?: settings.initialCapitalUsdt
            val initial = row?.initialCapitalUsdt ?: settings.initialCapitalUsdt
            val pnl = equity - initial
            val pnlPct = if (initial > 0) pnl / initial * 100.0 else 0.0

            val posLine = portfolio?.position?.let {
                "Posição: ${String.format(Locale.US, "%.6f", it.qtyBtc)} BTC @ " +
                    String.format(Locale.US, "%,.0f", it.entryPrice) +
                    " (SL ${String.format(Locale.US, "%,.0f", it.stopLoss)} / TP ${String.format(Locale.US, "%,.0f", it.takeProfit)})"
            } ?: "Posição: nenhuma"

            val lastDecision = db.decisionDao().latest()?.let {
                val a = when (it.action) { "BUY" -> "COMPRA"; "SELL" -> "VENDA"; else -> "ESPERA" }
                "Última decisão ($a, score ${String.format(Locale.US, "%.0f", it.score)}): " +
                    (it.reasons.split("\n").getOrNull(1) ?: "")
            } ?: "Última decisão: aguardando análise…"

            val trades = db.tradeDao().recent(6).map { t ->
                val side = if (t.side == "BUY") "🟢 COMPRA" else "🔴 VENDA"
                val pnlTxt = t.realizedPnlUsdt?.let {
                    String.format(Locale.US, " → %+.2f USDT", it)
                } ?: ""
                "${timeFmt.format(Date(t.time))} $side ${String.format(Locale.US, "%.5f", t.qtyBtc)} BTC$ pnlTxt"
            }

            val updated = row?.lastUpdatedAt?.let { "Atualizado: ${timeFmt.format(Date(it))}" }
                ?: "Atualizado: --"

            return WidgetState(
                botActive = settings.botEnabled,
                price = price,
                cashUsdt = portfolio?.cashUsdt ?: initial,
                equityUsdt = equity,
                initialCapitalUsdt = initial,
                pnlUsdt = pnl,
                pnlPct = pnlPct,
                positionLine = posLine,
                lastDecisionLine = lastDecision,
                lastTradesLines = trades,
                updatedAtLine = updated
            )
        }
    }
}
