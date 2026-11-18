package com.btcswing.simulator.strategy

import com.btcswing.simulator.domain.indicators.Candle
import com.btcswing.simulator.domain.indicators.Indicators
import com.btcswing.simulator.domain.indicators.IndicatorSnapshot
import com.btcswing.simulator.domain.model.BotSettings
import com.btcswing.simulator.domain.strategy.Action
import com.btcswing.simulator.domain.strategy.StrategyEngine
import com.btcswing.simulator.domain.strategy.Timeframe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StrategyEngineTest {

    private val settings = BotSettings(buyThreshold = 55.0, sellThreshold = 55.0)

    private fun snap(
        tf: Timeframe, rsi: Double, hist: Double, trend: com.btcswing.simulator.domain.indicators.Trend,
        adx: Double = 30.0, price: Double = 50_000.0
    ): IndicatorSnapshot = IndicatorSnapshot(
        timeframe = tf.label, price = price, rsi = rsi,
        macd = hist, macdSignal = 0.0, macdHistogram = hist,
        sma20 = if (trend == com.btcswing.simulator.domain.indicators.Trend.UP) price * 0.99 else price * 1.01,
        sma50 = if (trend == com.btcswing.simulator.domain.indicators.Trend.UP) price * 0.98 else price * 1.02,
        sma200 = price * 0.95, ema20 = price, ema50 = price, adx = adx,
        bollingerUpper = price * 1.02, bollingerMiddle = price, bollingerLower = price * 0.98,
        bollingerWidth = 0.04, obv = 0.0, atr = price * 0.01,
        volumeSma20 = 100.0, volumeRatio = 1.5, trend = trend,
        momentum = if (rsi > 55) com.btcswing.simulator.domain.indicators.Momentum.BULLISH
        else if (rsi < 45) com.btcswing.simulator.domain.indicators.Momentum.BEARISH
        else com.btcswing.simulator.domain.indicators.Momentum.NEUTRAL
    )

    private val bullishAll = mapOf(
        Timeframe.M5 to snap(Timeframe.M5, 62.0, 40.0, com.btcswing.simulator.domain.indicators.Trend.UP),
        Timeframe.M15 to snap(Timeframe.M15, 64.0, 60.0, com.btcswing.simulator.domain.indicators.Trend.UP),
        Timeframe.H1 to snap(Timeframe.H1, 66.0, 80.0, com.btcswing.simulator.domain.indicators.Trend.UP),
        Timeframe.H4 to snap(Timeframe.H4, 68.0, 100.0, com.btcswing.simulator.domain.indicators.Trend.UP),
        Timeframe.D1 to snap(Timeframe.D1, 70.0, 120.0, com.btcswing.simulator.domain.indicators.Trend.UP)
    )

    private val bearishAll = bullishAll.mapValues { (tf, s) ->
        s.copy(
            rsi = 100 - s.rsi, macdHistogram = -s.macdHistogram, macd = -s.macd,
            sma20 = s.price * 1.01, sma50 = s.price * 1.02,
            trend = com.btcswing.simulator.domain.indicators.Trend.DOWN,
            momentum = com.btcswing.simulator.domain.indicators.Momentum.BEARISH
        )
    }

    @Test fun `alta forte em todos os timeframes gera BUY sem posicao`() {
        val d = StrategyEngine.decide(bullishAll, settings, hasPosition = false, cooldownOk = true, lastTradeAgeMinutes = 999, time = 0)
        assertEquals(Action.BUY, d.action)
        assertTrue("score=${d.score} deveria ser >= 55", d.score >= settings.buyThreshold)
        assertTrue(d.reasons.isNotEmpty())
    }

    @Test fun `sinal de compra em cooldown vira WAIT`() {
        val d = StrategyEngine.decide(bullishAll, settings, hasPosition = false, cooldownOk = false, lastTradeAgeMinutes = 10, time = 0)
        assertEquals(Action.WAIT, d.action)
        assertTrue(d.reasons.any { "cooldown" in it })
    }

    @Test fun `com o bot ja comprado, alta forte nao recompra`() {
        val d = StrategyEngine.decide(bullishAll, settings, hasPosition = true, cooldownOk = true, lastTradeAgeMinutes = 999, time = 0)
        assertEquals(Action.WAIT, d.action)
    }

    @Test fun `queda forte com posicao aberta gera SELL`() {
        val d = StrategyEngine.decide(bearishAll, settings, hasPosition = true, cooldownOk = true, lastTradeAgeMinutes = 999, time = 0)
        assertEquals(Action.SELL, d.action)
        assertTrue("score=${d.score} deveria ser <= -55", d.score <= -settings.sellThreshold)
    }

    @Test fun `queda forte sem posicao nao vende a descoberto`() {
        // Long-only: sem posição, sinal negativo é apenas WAIT
        val d = StrategyEngine.decide(bearishAll, settings, hasPosition = false, cooldownOk = true, lastTradeAgeMinutes = 999, time = 0)
        assertEquals(Action.WAIT, d.action)
    }

    @Test fun `mercado misto gera WAIT`() {
        val mixed = mapOf(
            Timeframe.M5 to snap(Timeframe.M5, 50.0, 0.0, com.btcswing.simulator.domain.indicators.Trend.SIDEWAYS),
            Timeframe.H1 to snap(Timeframe.H1, 52.0, 5.0, com.btcswing.simulator.domain.indicators.Trend.SIDEWAYS),
            Timeframe.D1 to snap(Timeframe.D1, 48.0, -5.0, com.btcswing.simulator.domain.indicators.Trend.SIDEWAYS)
        )
        val d = StrategyEngine.decide(mixed, settings, hasPosition = false, cooldownOk = true, lastTradeAgeMinutes = 999, time = 0)
        assertEquals(Action.WAIT, d.action)
        assertTrue(kotlin.math.abs(d.score) < settings.buyThreshold)
    }

    @Test fun `sem dados nenhuns gera WAIT com aviso explicito`() {
        val d = StrategyEngine.decide(emptyMap(), settings, hasPosition = false, cooldownOk = true, lastTradeAgeMinutes = -1, time = 0)
        assertEquals(Action.WAIT, d.action)
        assertTrue(d.reasons.any { "inventada" in it })
    }

    @Test fun `score por timeframe fica no intervalo valido`() {
        bullishAll.values.forEach { s ->
            val sc = StrategyEngine.scoreTimeframe(s)
            assertTrue("score=$sc fora de [-1,1]", sc in -1.0..1.0)
        }
    }

    @Test fun `pesos dos timeframes somam 1`() {
        assertEquals(1.0, Timeframe.entries.sumOf { it.weight }, 1e-9)
    }

    @Test fun `snapshot real de candles sinteticos e aceito pela estrategia`() {
        val candles = (0 until 210).map { i ->
            val c = 100.0 + i
            Candle(i * 60_000L, c - 0.5, c + 1, c - 1, c, 10.0)
        }
        val snap = Indicators.snapshot(candles, "1h")
        assertNotNull(snap)
        val d = StrategyEngine.decide(mapOf(Timeframe.H1 to snap!!), settings, false, true, 999, 0)
        assertNotNull(d.action)
    }
}
