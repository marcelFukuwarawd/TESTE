package com.btcswing.simulator.indicators

import com.btcswing.simulator.domain.indicators.Candle
import com.btcswing.simulator.domain.indicators.Indicators
import com.btcswing.simulator.domain.indicators.Momentum
import com.btcswing.simulator.domain.indicators.Trend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IndicatorsTest {

    private fun series(vararg closes: Double) = closes.toList()

    /** Gera candles sintéticos em tendência de alta com ruído determinístico. */
    private fun uptrend(n: Int, start: Double = 100.0, step: Double = 1.0): List<Candle> =
        (0 until n).map { i ->
            val c = start + i * step
            Candle(
                openTime = i * 60_000L,
                open = c - 0.5, high = c + 1.0, low = c - 1.0, close = c,
                volume = 10.0 + (i % 5)
            )
        }

    @Test fun `sma simples confere`() {
        // SMA usa a janela MAIS RECENTE (takeLast): (3+4+5)/3 = 4
        assertEquals(4.0, Indicators.sma(series(1.0, 2.0, 3.0, 4.0, 5.0), 3), 1e-9)
        assertTrue(Indicators.sma(series(1.0, 2.0), 5).isNaN())
    }

    /** Alta acelerada (convexa): é o cenário em que a EMA responde mais rápido que a SMA. */
    private fun acceleratingUptrend(n: Int, start: Double = 100.0): List<Candle> =
        (0 until n).map { i ->
            val c = start + i + 0.02 * i * i
            Candle(
                openTime = i * 60_000L,
                open = c - 0.5, high = c + 1.0, low = c - 1.0, close = c,
                volume = 10.0 + (i % 5)
            )
        }

    @Test fun `ema responde mais rapido que sma na alta acelerada`() {
        val up = acceleratingUptrend(60).map { it.close }
        assertTrue("EMA20 deve ficar acima da SMA20 quando a alta acelera", Indicators.ema(up, 20) > Indicators.sma(up, 20))
    }

    @Test fun `rsi de alta forte fica acima de 70`() {
        val up = uptrend(40).map { it.close }
        val rsi = Indicators.rsi(up)
        assertTrue("RSI=$rsi deveria ser > 70 em alta monotônica", rsi > 70)
        assertTrue(rsi <= 100.0)
    }

    @Test fun `rsi de baixa forte fica abaixo de 30`() {
        val down = uptrend(40).map { it.close }.reversed()
        val rsi = Indicators.rsi(down)
        assertTrue("RSI=$rsi deveria ser < 30 em queda monotônica", rsi < 30)
    }

    @Test fun `rsi sem dados suficientes retorna NaN`() {
        assertTrue(Indicators.rsi(series(1.0, 2.0, 3.0), 14).isNaN())
    }

    @Test fun `macd positivo em tendencia de alta`() {
        val up = uptrend(80).map { it.close }
        val m = Indicators.macd(up)
        assertFalse(m.macd.isNaN())
        assertTrue("MACD=${m.macd} deveria ser > 0 em alta", m.macd > 0)
        assertTrue(m.histogram.isFinite())
    }

    @Test fun `bollinger tem media igual a sma20 e bandas simetricas`() {
        val closes = uptrend(50).map { it.close }
        val b = Indicators.bollinger(closes)
        assertEquals(Indicators.sma(closes, 20), b.middle, 1e-9)
        assertTrue(b.upper > b.middle && b.middle > b.lower)
        assertEquals(b.upper - b.middle, b.middle - b.lower, 1e-9)
    }

    @Test fun `atr e positivo e finito`() {
        val atr = Indicators.atr(uptrend(40))
        assertTrue(atr.isFinite() && atr > 0)
    }

    @Test fun `obv sobe quando todos os fechamentos sobem`() {
        val obv = Indicators.obv(uptrend(30))
        assertTrue(obv > 0)
    }

    @Test fun `adx fica entre 0 e 100 e e alto em tendencia forte`() {
        val adx = Indicators.adx(uptrend(60))
        assertTrue("ADX=$adx fora do intervalo", adx in 0.0..100.0)
        assertTrue("ADX=$adx deveria indicar tendência forte", adx > 25.0)
    }

    @Test fun `snapshot detecta tendencia de alta com momentum`() {
        val snap = Indicators.snapshot(acceleratingUptrend(210), "1h")
        assertNotNull(snap)
        snap!!
        assertEquals(Trend.UP, snap.trend)
        assertEquals(Momentum.BULLISH, snap.momentum)
        assertTrue(snap.sma200.isFinite())
        assertTrue(snap.atr > 0)
        assertTrue(snap.volumeSma20 > 0)
    }

    @Test fun `snapshot retorna null com candles insuficientes`() {
        assertEquals(null, Indicators.snapshot(uptrend(10), "5m"))
    }
}
