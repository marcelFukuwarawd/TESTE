package com.btcswing.simulator.domain.strategy

import com.btcswing.simulator.domain.indicators.IndicatorSnapshot
import com.btcswing.simulator.domain.indicators.Momentum
import com.btcswing.simulator.domain.indicators.Trend
import com.btcswing.simulator.domain.model.BotSettings
import kotlin.math.abs

enum class Action { BUY, SELL, WAIT }

/** Decisão final do robô, com justificativas legíveis (gravadas no histórico). */
data class TradeDecision(
    val action: Action,
    /** Score agregado em [-100, +100]: positivo = viés comprador. */
    val score: Double,
    /** Score por timeframe (rótulo → score [-100,100]). */
    val scoresByTimeframe: Map<String, Double>,
    val reasons: List<String>,
    val price: Double,
    val atr1h: Double?,
    val time: Long
)

/**
 * Estratégia multi-timeframe puramente técnica (sem ML, sem dados externos
 * além dos candles reais). Função pura — coberta por testes unitários.
 *
 * Score por timeframe em [-1, +1] ponderado pelos pesos (D1 25%, H4 20%,
 * H1 20%, M15 15%, M10 10%, M5 10%) → score final em [-100, +100].
 *
 *  - BUY  quando score >= buyThreshold, sem posição aberta e cooldown ok.
 *  - SELL quando score <= -sellThreshold com posição aberta (stop-loss e
 *    take-profit são verificados separadamente pelo SimulationEngine).
 *  - WAIT em qualquer outro caso, incluindo dados insuficientes — o robô
 *    NUNCA inventa operação sem candles reais.
 */
object StrategyEngine {

    /** Calcula o score de um timeframe individual em [-1, +1]. */
    fun scoreTimeframe(s: IndicatorSnapshot): Double {
        var score = 0.0

        // 1) Tendência (SMA20 vs SMA50 + preço) — peso 0.35
        score += 0.35 * when (s.trend) {
            Trend.UP -> 1.0
            Trend.DOWN -> -1.0
            Trend.SIDEWAYS -> 0.0
        }

        // 2) Momentum MACD (histograma normalizado pelo preço) — peso 0.25
        if (s.macdHistogram.isFinite() && s.price > 0) {
            val normHist = (s.macdHistogram / s.price) * 10_000.0 // ~bps
            score += 0.25 * normHist.coerceIn(-1.0, 1.0)
        }

        // 3) RSI — peso 0.20 (contrário em extremos, a favor no meio)
        if (s.rsi.isFinite()) {
            score += 0.20 * when {
                s.rsi <= 30 -> 1.0            // sobrevendido → viés de compra
                s.rsi >= 70 -> -1.0           // sobrecomprado → viés de venda
                s.rsi in 45.0..55.0 -> 0.0
                s.rsi > 55 -> ((s.rsi - 55.0) / 15.0).coerceAtMost(1.0)
                else -> -((55.0 - s.rsi) / 25.0).coerceAtMost(1.0)
            }
        }

        // 4) Bollinger — peso 0.10 (reversão à média nas bandas)
        if (s.bollingerUpper.isFinite() && s.bollingerLower.isFinite() &&
            s.bollingerUpper > s.bollingerLower
        ) {
            val pos = (s.price - s.bollingerLower) / (s.bollingerUpper - s.bollingerLower)
            score += 0.10 * when {
                pos <= 0.0 -> 1.0   // abaixo da banda inferior
                pos >= 1.0 -> -1.0  // acima da banda superior
                else -> 0.0
            }
        }

        // 5) Volume/OBV — peso 0.10 (confirmação)
        if (s.momentum != Momentum.NEUTRAL && s.volumeRatio > 1.2) {
            score += 0.10 * if (s.momentum == Momentum.BULLISH) 1.0 else -1.0
        }

        // ADX modula a confiança: tendência forte amplifica, mercado lateral reduz
        if (s.adx.isFinite()) {
            val adxFactor = when {
                s.adx >= 40 -> 1.15
                s.adx >= 25 -> 1.0
                s.adx >= 20 -> 0.85
                else -> 0.7
            }
            score *= adxFactor
        }

        return score.coerceIn(-1.0, 1.0)
    }

    /**
     * Decisão agregada multi-timeframe.
     *
     * @param snapshots    indicadores por TF (apenas TFs com dados reais)
     * @param hasPosition  se há posição aberta
     * @param cooldownOk   se o cooldown desde a última operação já passou
     * @param lastTradeAgeMinutes idade da última operação (para o log)
     */
    fun decide(
        snapshots: Map<Timeframe, IndicatorSnapshot>,
        settings: BotSettings,
        hasPosition: Boolean,
        cooldownOk: Boolean,
        lastTradeAgeMinutes: Long,
        time: Long
    ): TradeDecision {
        val reasons = mutableListOf<String>()

        if (snapshots.isEmpty()) {
            return TradeDecision(Action.WAIT, 0.0, emptyMap(),
                listOf("sem dados de candles reais — nenhuma operação é inventada"), 0.0, null, time)
        }

        // Score ponderado; normaliza pelo peso dos TFs efetivamente disponíveis
        val scoresByTf = mutableMapOf<String, Double>()
        var weighted = 0.0
        var totalWeight = 0.0
        for ((tf, snap) in snapshots) {
            val s = scoreTimeframe(snap)
            scoresByTf[tf.label] = s * 100.0
            weighted += s * tf.weight
            totalWeight += tf.weight
        }
        val score = if (totalWeight > 0) (weighted / totalWeight) * 100.0 else 0.0

        // Todos os snapshots carregam o último preço real do mesmo mercado
        val price = snapshots.values.firstOrNull { it.price.isFinite() && it.price > 0 }?.price ?: 0.0
        val atr1h = snapshots[Timeframe.H1]?.atr?.takeIf { it.isFinite() }

        // Contexto para o log/widget
        snapshots.entries
            .sortedByDescending { it.key.weight }
            .forEach { (tf, s) ->
                reasons.add(
                    "${tf.label}: RSI ${fmt(s.rsi)}, MACD hist ${fmt(s.macdHistogram)}, " +
                        "ADX ${fmt(s.adx)}, tendência ${s.trend.name.lowercase()}, score ${fmt(scoresByTf[tf.label] ?: 0.0)}"
                )
            }

        val action = when {
            !hasPosition && score >= settings.buyThreshold && cooldownOk -> Action.BUY
            !hasPosition && score >= settings.buyThreshold && !cooldownOk -> {
                reasons.add("sinal de compra, mas em cooldown (${lastTradeAgeMinutes}min < ${settings.cooldownMinutes}min)")
                Action.WAIT
            }
            hasPosition && score <= -settings.sellThreshold -> Action.SELL
            else -> Action.WAIT
        }

        reasons.add(0, "Decisão: ${action.name} (score agregado ${fmt(score)}; " +
            "limiar compra ${fmt(settings.buyThreshold)}, venda ${fmt(-settings.sellThreshold)})")

        return TradeDecision(action, score, scoresByTf, reasons, price, atr1h, time)
    }

    private fun fmt(v: Double) = if (v.isFinite()) String.format(java.util.Locale.US, "%.2f", v) else "--"
}
