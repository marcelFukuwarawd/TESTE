package com.btcswing.simulator.domain.simulation

/** Ponto de patrimônio (para drawdown e desempenho acumulado). */
data class EquityPoint(val time: Long, val equityUsdt: Double)

/** Relatório diário gerado a cada 24h no horário escolhido pelo usuário. */
data class DailyReport(
    val generatedAt: Long,
    val windowStart: Long,
    val windowEnd: Long,
    val tradeCount: Int,
    val winCount: Int,
    val lossCount: Int,
    /** PnL realizado das vendas fechadas dentro da janela (USDT). */
    val realizedPnlUsdt: Double,
    /** PnL realizado em % sobre o patrimônio no início da janela. */
    val realizedPnlPct: Double,
    /** Saldo em caixa no fim da janela (USDT). */
    val finalBalanceUsdt: Double,
    /** Patrimônio total (caixa + posição marcada a mercado) no fim da janela. */
    val finalEquityUsdt: Double,
    /** Lucro/prejuízo acumulado desde o início da simulação (USDT). */
    val cumulativePnlUsdt: Double,
    val cumulativePnlPct: Double,
    /** Maior queda de patrimônio registrada na janela (%). */
    val maxDrawdownPct: Double,
    val bestTradePnlUsdt: Double?,
    val worstTradePnlUsdt: Double?,
    /** Linhas legíveis exibidas na notificação/tela de relatórios. */
    val summaryLines: List<String>
)

/**
 * Monta o relatório a partir de dados REAIS persistidos. Função pura —
 * coberta por testes unitários. Se não houver operações na janela, o
 * relatório diz isso explicitamente (nada é inventado).
 */
object ReportBuilder {

    fun build(
        tradesInWindow: List<TradeRecord>,
        equityPointsInWindow: List<EquityPoint>,
        initialCapitalUsdt: Double,
        currentPortfolio: Portfolio,
        currentPrice: Double,
        windowStart: Long,
        windowEnd: Long
    ): DailyReport {
        val sells = tradesInWindow.filter { it.side == Side.SELL && it.realizedPnlUsdt != null }
        val realized = sells.sumOf { it.realizedPnlUsdt ?: 0.0 }
        val wins = sells.count { (it.realizedPnlUsdt ?: 0.0) > 0 }
        val losses = sells.count { (it.realizedPnlUsdt ?: 0.0) <= 0 }

        val equityAtStart = equityPointsInWindow
            .filter { it.time <= windowStart + 60_000 }
            .minByOrNull { abs(it.time - windowStart) }?.equityUsdt
            ?: equityPointsInWindow.minByOrNull { it.time }?.equityUsdt
            ?: initialCapitalUsdt
        val equityAtEnd = currentPortfolio.equity(if (currentPrice > 0) currentPrice else 0.0)
            .let { if (equityPointsInWindow.isNotEmpty()) equityPointsInWindow.maxByOrNull { p -> p.time }?.equityUsdt ?: it else it }

        val realizedPct = if (equityAtStart > 0) realized / equityAtStart * 100.0 else 0.0
        val cumulative = equityAtEnd - initialCapitalUsdt
        val cumulativePct = if (initialCapitalUsdt > 0) cumulative / initialCapitalUsdt * 100.0 else 0.0

        val drawdown = maxDrawdownPct(equityPointsInWindow)

        val best = sells.maxByOrNull { it.realizedPnlUsdt ?: Double.MIN_VALUE }?.realizedPnlUsdt
        val worst = sells.minByOrNull { it.realizedPnlUsdt ?: Double.MAX_VALUE }?.realizedPnlUsdt

        val lines = buildList {
            add("Janela: ${fmt(windowStart)} → ${fmt(windowEnd)}")
            if (tradesInWindow.isEmpty()) {
                add("Nenhuma operação no período — o robô permaneceu em espera (WAIT).")
            } else {
                add("Operações: ${tradesInWindow.size} (${sells.size} vendas fechadas, $wins ganhos, $losses perdas)")
                add("PnL realizado: ${usdt(realized)} (${pct(realizedPct)})")
                if (best != null) add("Melhor venda: ${usdt(best)}")
                if (worst != null) add("Pior venda: ${usdt(worst)}")
            }
            add("Saldo em caixa: ${usdt(currentPortfolio.cashUsdt)}")
            add("Patrimônio total: ${usdt(equityAtEnd)}")
            add("Acumulado desde o início: ${usdt(cumulative)} (${pct(cumulativePct)})")
            add("Drawdown máximo na janela: ${pct(drawdown)}")
            currentPortfolio.position?.let {
                add("Posição aberta: ${it.qtyBtc} BTC @ ${usdt(it.entryPrice)} (SL ${usdt(it.stopLoss)} / TP ${usdt(it.takeProfit)})")
            } ?: add("Posição aberta: nenhuma")
        }

        return DailyReport(
            generatedAt = windowEnd,
            windowStart = windowStart,
            windowEnd = windowEnd,
            tradeCount = tradesInWindow.size,
            winCount = wins,
            lossCount = losses,
            realizedPnlUsdt = realized,
            realizedPnlPct = realizedPct,
            finalBalanceUsdt = currentPortfolio.cashUsdt,
            finalEquityUsdt = equityAtEnd,
            cumulativePnlUsdt = cumulative,
            cumulativePnlPct = cumulativePct,
            maxDrawdownPct = drawdown,
            bestTradePnlUsdt = best,
            worstTradePnlUsdt = worst,
            summaryLines = lines
        )
    }

    /** Drawdown máximo (%) a partir dos pontos de patrimônio. */
    fun maxDrawdownPct(points: List<EquityPoint>): Double {
        if (points.size < 2) return 0.0
        var peak = points.first().equityUsdt
        var maxDd = 0.0
        for (p in points.sortedBy { it.time }) {
            if (p.equityUsdt > peak) peak = p.equityUsdt
            if (peak > 0) {
                val dd = (peak - p.equityUsdt) / peak * 100.0
                if (dd > maxDd) maxDd = dd
            }
        }
        return maxDd
    }

    private fun abs(v: Long) = if (v < 0) -v else v
    private fun usdt(v: Double) = String.format(java.util.Locale.US, "%,.2f USDT", v)
    private fun pct(v: Double) = String.format(java.util.Locale.US, "%+.2f%%", v)
    private fun fmt(epochMillis: Long): String =
        java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(epochMillis))
}
