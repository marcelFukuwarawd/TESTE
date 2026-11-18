package com.btcswing.simulator.service

import com.btcswing.simulator.data.api.CandlesResult
import com.btcswing.simulator.data.api.MarketRepository
import com.btcswing.simulator.data.db.AppDatabase
import com.btcswing.simulator.data.db.DecisionEntity
import com.btcswing.simulator.data.db.EquityEntity
import com.btcswing.simulator.data.db.PortfolioEntity
import com.btcswing.simulator.data.db.toDomain
import com.btcswing.simulator.data.db.toEntity
import com.btcswing.simulator.data.prefs.SettingsRepository
import com.btcswing.simulator.domain.indicators.IndicatorSnapshot
import com.btcswing.simulator.domain.indicators.Indicators
import com.btcswing.simulator.domain.model.BotSettings
import com.btcswing.simulator.domain.simulation.ExecutionResult
import com.btcswing.simulator.domain.simulation.Portfolio
import com.btcswing.simulator.domain.simulation.SimulationEngine
import com.btcswing.simulator.domain.simulation.TradeRecord
import com.btcswing.simulator.domain.strategy.Action
import com.btcswing.simulator.domain.strategy.StrategyEngine
import com.btcswing.simulator.domain.strategy.Timeframe
import com.btcswing.simulator.domain.strategy.TradeDecision
import org.json.JSONObject

/** Resultado de um ciclo de análise (usado por serviço, widget e UI). */
data class CycleOutcome(
    val ok: Boolean,
    val time: Long,
    val message: String,
    val price: Double,
    val portfolio: Portfolio,
    val decision: TradeDecision?,
    val snapshots: Map<Timeframe, IndicatorSnapshot>,
    val executedTrades: List<TradeRecord>,
    val dataSources: Map<String, String>,   // TF → fonte (Binance/OKX/cache)
    val staleData: Boolean,
    val settings: BotSettings
)

/**
 * Orquestra um ciclo completo:
 *  busca candles reais → indicadores → decisão → execução simulada → persistência.
 *
 * Princípios:
 *  - Sem dados reais, NENHUMA operação é criada (decisão WAIT com motivo).
 *  - Tudo é persistido antes de notificar — se o processo morrer no meio,
 *    o estado consistente está no banco e o serviço retoma de onde parou.
 *  - Nunca lança exceção para fora de runCycle (o serviço continua vivo).
 */
class TradingEngine(
    private val marketRepository: MarketRepository,
    private val settingsRepository: SettingsRepository,
    private val db: AppDatabase
) {

    suspend fun runCycle(now: Long = System.currentTimeMillis()): CycleOutcome {
        val settings = settingsRepository.current()
        val pstate = loadOrCreatePortfolio(settings, now)
        var current: Portfolio = pstate.portfolio

        // 1) Candles reais de todos os timeframes (com fallback e cache)
        val fetched: Map<Timeframe, CandlesResult> = try {
            marketRepository.allTimeframes(settings.symbol, limit = 300, now = now)
        } catch (e: Exception) {
            return outcome(now, "Falha de rede ao buscar candles: ${e.message}", settings, pstate,
                price = 0.0, decision = null, snapshots = emptyMap(), trades = emptyList(),
                sources = emptyMap(), stale = true)
        }

        // 2) Indicadores por timeframe
        val snapshots = mutableMapOf<Timeframe, IndicatorSnapshot>()
        for ((tf, result) in fetched) {
            if (result.candles.size >= 30) {
                Indicators.snapshot(result.candles, tf.label)?.let { snapshots[tf] = it }
            }
        }
        val sources = fetched
            .mapValues { it.value.source + if (it.value.stale) " (cache)" else "" }
            .mapKeys { it.key.label }
        val stale = fetched.values.all { it.stale }

        val price = snapshots.values.firstOrNull { it.price.isFinite() && it.price > 0 }?.price
            ?: pstate.lastPrice

        if (snapshots.isEmpty() || price <= 0.0) {
            val decision = TradeDecision(Action.WAIT, 0.0, emptyMap(),
                listOf("Dados insuficientes no momento — aguardando candles reais. Nenhuma operação inventada."),
                price, null, now)
            persistDecision(decision, executed = false, now)
            return outcome(now, "Aguardando dados de mercado", settings, pstate, price, decision, snapshots, emptyList(), sources, stale)
        }

        // 3) Cooldown desde a última operação
        val lastTradeTime = db.tradeDao().lastTradeTime()
        val ageMin = if (lastTradeTime != null) (now - lastTradeTime) / 60_000 else Long.MAX_VALUE
        val cooldownOk = ageMin >= settings.cooldownMinutes

        // 4) Decisão agregada
        val decision = StrategyEngine.decide(
            snapshots = snapshots,
            settings = settings,
            hasPosition = current.position != null,
            cooldownOk = cooldownOk,
            lastTradeAgeMinutes = if (ageMin == Long.MAX_VALUE) -1 else ageMin,
            time = now
        )

        // 5) Execução simulada
        val executed = mutableListOf<TradeRecord>()
        val atr1h = snapshots[Timeframe.H1]?.atr

        current.position?.let { pos ->
            // Stop-loss / take-profit têm prioridade sobre o sinal
            val stopReason = SimulationEngine.checkStops(pos, price)
            val sellReason = when {
                stopReason != null -> stopReason
                decision.action == Action.SELL -> "sinal de venda multi-timeframe (score ${fmt2(decision.score)})"
                else -> null
            }
            if (sellReason != null) {
                val r = SimulationEngine.sell(current, price, settings, now, sellReason, decision.score)
                if (r is ExecutionResult.Executed) { executed += r.trade; current = r.portfolio }
            }
        }

        var finalDecision = decision
        if (executed.isEmpty() && decision.action == Action.BUY && current.position == null && cooldownOk) {
            val r = SimulationEngine.buy(current, price, settings, now,
                "sinal de compra multi-timeframe (score ${fmt2(decision.score)})", decision.score, atr1h)
            when (r) {
                is ExecutionResult.Executed -> { executed += r.trade; current = r.portfolio }
                is ExecutionResult.Rejected ->
                    finalDecision = decision.copy(reasons = decision.reasons + "Compra não executada: ${r.reason}")
            }
        }

        // 6) Persistência (estado → trades → decisões → equity)
        savePortfolio(current, settings, price, now, executed.lastOrNull()?.time ?: lastTradeTime)
        executed.forEach { db.tradeDao().insert(it.toEntity()) }
        persistDecision(finalDecision, executed.isNotEmpty(), now)
        db.equityDao().insert(EquityEntity(time = now, equityUsdt = current.equity(price), cashUsdt = current.cashUsdt, price = price))
        db.decisionDao().prune(now - 30L * 24 * 60 * 60 * 1000) // mantém 30 dias de log

        val msg = buildString {
            append("${finalDecision.action.name} • score ${fmt2(finalDecision.score)} • $${fmt2(price)}")
            if (stale) append(" • ⚠ dados em cache")
            executed.forEach {
                append(" • ")
                append(if (it.side == com.btcswing.simulator.domain.simulation.Side.BUY) "COMPRA" else "VENDA")
                append(" ${fmt6(it.qtyBtc)} BTC")
                it.realizedPnlUsdt?.let { p -> append(" (PnL ${fmt2(p)} USDT)") }
            }
        }
        return outcome(now, msg, settings, pstate, price, finalDecision, snapshots, executed, sources, stale)
    }

    /** Reinicializa a simulação com novo capital inicial (apaga todo o histórico). */
    suspend fun resetSimulation(initialCapital: Double, now: Long = System.currentTimeMillis()) {
        db.tradeDao().clear(); db.decisionDao().clear(); db.equityDao().clear()
        db.reportDao().clear(); db.candleDao().clear()
        db.portfolioDao().upsert(
            PortfolioEntity(
                id = 1, cashUsdt = initialCapital, initialCapitalUsdt = initialCapital,
                posQtyBtc = null, posEntryPrice = null, posOpenedAt = null, posCostBasis = null,
                posStopLoss = null, posTakeProfit = null,
                lastTradeTime = null, lastPrice = null, lastUpdatedAt = now
            )
        )
        db.equityDao().insert(EquityEntity(time = now, equityUsdt = initialCapital, cashUsdt = initialCapital, price = 0.0))
    }

    /**
     * Gera o relatório das últimas 24h (chamado pelo DailyReportWorker no
     * horário escolhido). Usa apenas dados reais persistidos.
     */
    suspend fun generateDailyReport(now: Long = System.currentTimeMillis()): com.btcswing.simulator.domain.simulation.DailyReport {
        val settings = settingsRepository.current()
        val ps = loadOrCreatePortfolio(settings, now)
        val windowStart = now - 24L * 60 * 60 * 1000
        val trades = db.tradeDao().since(windowStart).map { it.toDomain() }
        val equityPoints = db.equityDao().since(windowStart)
            .map { com.btcswing.simulator.domain.simulation.EquityPoint(it.time, it.equityUsdt) }
        val price = ps.lastPrice.takeIf { it > 0 } ?: trades.lastOrNull()?.marketPrice ?: 0.0

        val report = com.btcswing.simulator.domain.simulation.ReportBuilder.build(
            tradesInWindow = trades,
            equityPointsInWindow = equityPoints,
            initialCapitalUsdt = ps.initialCapital,
            currentPortfolio = ps.portfolio,
            currentPrice = price,
            windowStart = windowStart,
            windowEnd = now
        )

        db.reportDao().insert(
            com.btcswing.simulator.data.db.ReportEntity(
                generatedAt = report.generatedAt,
                windowStart = report.windowStart,
                windowEnd = report.windowEnd,
                tradeCount = report.tradeCount,
                winCount = report.winCount,
                lossCount = report.lossCount,
                realizedPnlUsdt = report.realizedPnlUsdt,
                realizedPnlPct = report.realizedPnlPct,
                finalBalanceUsdt = report.finalBalanceUsdt,
                finalEquityUsdt = report.finalEquityUsdt,
                cumulativePnlUsdt = report.cumulativePnlUsdt,
                cumulativePnlPct = report.cumulativePnlPct,
                maxDrawdownPct = report.maxDrawdownPct,
                bestTradePnlUsdt = report.bestTradePnlUsdt,
                worstTradePnlUsdt = report.worstTradePnlUsdt,
                summaryLines = report.summaryLines.joinToString("\n")
            )
        )
        return report
    }

    // ------------------------------------------------------------------ utils

    private suspend fun loadOrCreatePortfolio(settings: BotSettings, now: Long): PortfolioState {
        val row = db.portfolioDao().get()
        if (row != null) return PortfolioState(row.toDomain(), row.lastPrice ?: 0.0, row.initialCapitalUsdt)
        val initial = Portfolio(cashUsdt = settings.initialCapitalUsdt)
        db.portfolioDao().upsert(
            PortfolioEntity(
                id = 1, cashUsdt = settings.initialCapitalUsdt, initialCapitalUsdt = settings.initialCapitalUsdt,
                posQtyBtc = null, posEntryPrice = null, posOpenedAt = null, posCostBasis = null,
                posStopLoss = null, posTakeProfit = null, lastTradeTime = null, lastPrice = null, lastUpdatedAt = now
            )
        )
        db.equityDao().insert(EquityEntity(time = now, equityUsdt = settings.initialCapitalUsdt, cashUsdt = settings.initialCapitalUsdt, price = 0.0))
        return PortfolioState(initial, 0.0, settings.initialCapitalUsdt)
    }

    private data class PortfolioState(val portfolio: Portfolio, val lastPrice: Double, val initialCapital: Double)

    private suspend fun savePortfolio(p: Portfolio, s: BotSettings, price: Double, now: Long, lastTradeTime: Long?) {
        val pos = p.position
        db.portfolioDao().upsert(
            PortfolioEntity(
                id = 1,
                cashUsdt = p.cashUsdt,
                initialCapitalUsdt = s.initialCapitalUsdt,
                posQtyBtc = pos?.qtyBtc, posEntryPrice = pos?.entryPrice, posOpenedAt = pos?.openedAt,
                posCostBasis = pos?.costBasisUsdt, posStopLoss = pos?.stopLoss, posTakeProfit = pos?.takeProfit,
                lastTradeTime = lastTradeTime, lastPrice = price, lastUpdatedAt = now
            )
        )
    }

    private suspend fun persistDecision(d: TradeDecision, executed: Boolean, now: Long) {
        val scoresJson = JSONObject().apply {
            d.scoresByTimeframe.forEach { (k, v) -> put(k, v) }
        }.toString()
        db.decisionDao().insert(
            DecisionEntity(
                time = d.time, action = d.action.name, score = d.score, price = d.price,
                reasons = d.reasons.joinToString("\n"), scoresByTf = scoresJson, executed = executed
            )
        )
    }

    private fun outcome(
        now: Long, msg: String, s: BotSettings, ps: PortfolioState, price: Double,
        decision: TradeDecision?, snapshots: Map<Timeframe, IndicatorSnapshot>,
        trades: List<TradeRecord>, sources: Map<String, String>, stale: Boolean
    ) = CycleOutcome(
        ok = true, time = now, message = msg, price = if (price > 0) price else ps.lastPrice,
        portfolio = ps.portfolio, decision = decision, snapshots = snapshots,
        executedTrades = trades, dataSources = sources, staleData = stale, settings = s
    )

    private fun fmt2(v: Double) = String.format(java.util.Locale.US, "%.2f", v)
    private fun fmt6(v: Double) = String.format(java.util.Locale.US, "%.6f", v)
}
