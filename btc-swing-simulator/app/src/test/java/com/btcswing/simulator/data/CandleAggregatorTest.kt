package com.btcswing.simulator.data

import com.btcswing.simulator.domain.indicators.Candle
import com.btcswing.simulator.domain.indicators.CandleAggregator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CandleAggregatorTest {

    private val fiveMin = 5 * 60_000L

    private fun c(i: Int, o: Double, h: Double, l: Double, cl: Double, v: Double) =
        Candle(openTime = i * fiveMin, open = o, high = h, low = l, close = cl, volume = v)

    @Test fun `dois candles de 5m viram um de 10m com OHLCV correto`() {
        val candles = listOf(
            c(0, 100.0, 110.0, 95.0, 105.0, 10.0),
            c(1, 105.0, 120.0, 100.0, 98.0, 20.0)
        )
        val out = CandleAggregator.fiveToTenMinutes(candles)
        assertEquals(1, out.size)
        val agg = out[0]
        assertEquals(0L, agg.openTime)
        assertEquals(100.0, agg.open, 1e-9)
        assertEquals(120.0, agg.high, 1e-9)
        assertEquals(95.0, agg.low, 1e-9)
        assertEquals(98.0, agg.close, 1e-9)
        assertEquals(30.0, agg.volume, 1e-9)
    }

    @Test fun `agrupamento alinhado a epoch coloca cada candle no bucket certo`() {
        // openTimes: 5m, 10m, 15m, 20m → buckets de 10m: [0-10m), [10m-20m), [20m-30m)
        val candles = listOf(
            c(1, 1.0, 2.0, 0.5, 1.5, 5.0),   // 5m  → bucket 0 (sozinho)
            c(2, 1.5, 3.0, 1.0, 2.5, 5.0),   // 10m → bucket 1
            c(3, 2.5, 4.0, 2.0, 3.5, 5.0),   // 15m → bucket 1
            c(4, 3.5, 5.0, 3.0, 4.5, 5.0)    // 20m → bucket 2 (sozinho)
        )
        val out = CandleAggregator.fiveToTenMinutes(candles)
        assertEquals(3, out.size)
        assertEquals(0L, out[0].openTime)
        assertEquals(1.0, out[0].open, 1e-9)
        assertEquals(1.5, out[0].close, 1e-9)
        assertEquals(600_000L, out[1].openTime)  // bucket 00:10
        assertEquals(1.5, out[1].open, 1e-9)     // abre no candle de 10m
        assertEquals(4.0, out[1].high, 1e-9)     // high agregado
        assertEquals(1.0, out[1].low, 1e-9)      // low agregado
        assertEquals(3.5, out[1].close, 1e-9)    // fecha no candle de 15m
        assertEquals(10.0, out[1].volume, 1e-9)  // volume somado
        assertEquals(1_200_000L, out[2].openTime) // bucket 00:20
        assertEquals(4.5, out[2].close, 1e-9)
    }

    @Test fun `lista vazia ou bucket invalido nao quebra`() {
        assertTrue(CandleAggregator.aggregate(emptyList(), 1000).isEmpty())
        assertTrue(CandleAggregator.aggregate(listOf(c(0, 1.0, 1.0, 1.0, 1.0, 1.0)), 0).isEmpty())
    }

    @Test fun `ordem de entrada fora de sequencia e corrigida`() {
        val candles = listOf(c(1, 105.0, 120.0, 100.0, 98.0, 20.0), c(0, 100.0, 110.0, 95.0, 105.0, 10.0))
        val out = CandleAggregator.fiveToTenMinutes(candles)
        assertEquals(1, out.size)
        assertEquals(100.0, out[0].open, 1e-9)
        assertEquals(98.0, out[0].close, 1e-9)
    }
}
