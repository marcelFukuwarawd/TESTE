package com.btcswing.simulator.domain.indicators

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/** Candle de mercado (preços de fechamento/abertura etc.). */
data class Candle(
    val openTime: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double,
    val closeTime: Long = openTime
)

/** Resultado consolidado dos indicadores em um timeframe. */
data class IndicatorSnapshot(
    val timeframe: String,
    val price: Double,
    val rsi: Double,
    val macd: Double,
    val macdSignal: Double,
    val macdHistogram: Double,
    val sma20: Double,
    val sma50: Double,
    val sma200: Double,
    val ema20: Double,
    val ema50: Double,
    val adx: Double,
    val bollingerUpper: Double,
    val bollingerMiddle: Double,
    val bollingerLower: Double,
    val bollingerWidth: Double,
    val obv: Double,
    val atr: Double,
    val volumeSma20: Double,
    val volumeRatio: Double,
    val trend: Trend,
    val momentum: Momentum
)

enum class Trend { UP, DOWN, SIDEWAYS }
enum class Momentum { BULLISH, BEARISH, NEUTRAL }

object Indicators {

    fun sma(values: List<Double>, period: Int): Double {
        if (values.size < period || period <= 0) return Double.NaN
        return values.takeLast(period).average()
    }

    fun ema(values: List<Double>, period: Int): Double {
        if (values.isEmpty() || period <= 0) return Double.NaN
        if (values.size < period) return values.average()
        val k = 2.0 / (period + 1)
        var ema = values.take(period).average()
        for (i in period until values.size) ema = values[i] * k + ema * (1 - k)
        return ema
    }

    /** RSI de Wilder (14 por padrão). Retorna 0..100, ou NaN se dados insuficientes. */
    fun rsi(closes: List<Double>, period: Int = 14): Double {
        if (closes.size < period + 1) return Double.NaN
        var gains = 0.0; var losses = 0.0
        for (i in 1..period) {
            val d = closes[i] - closes[i - 1]
            if (d >= 0) gains += d else losses += -d
        }
        var avgGain = gains / period
        var avgLoss = losses / period
        for (i in period + 1 until closes.size) {
            val d = closes[i] - closes[i - 1]
            avgGain = (avgGain * (period - 1) + max(d, 0.0)) / period
            avgLoss = (avgLoss * (period - 1) + max(-d, 0.0)) / period
        }
        if (avgLoss == 0.0) return 100.0
        val rs = avgGain / avgLoss
        return 100.0 - 100.0 / (1.0 + rs)
    }

    data class MacdResult(val macd: Double, val signal: Double, val histogram: Double)

    fun macd(closes: List<Double>, fast: Int = 12, slow: Int = 26, signalPeriod: Int = 9): MacdResult {
        if (closes.size < slow + signalPeriod) return MacdResult(Double.NaN, Double.NaN, Double.NaN)
        // Série MACD completa para calcular a linha de sinal corretamente
        val macdSeries = mutableListOf<Double>()
        for (end in slow..closes.size) {
            val slice = closes.subList(0, end)
            macdSeries.add(ema(slice, fast) - ema(slice, slow))
        }
        val macdLine = macdSeries.last()
        val signal = ema(macdSeries, signalPeriod)
        return MacdResult(macdLine, signal, macdLine - signal)
    }

    /** ADX simplificado (Wilder). */
    fun adx(candles: List<Candle>, period: Int = 14): Double {
        if (candles.size < period * 2 + 1) return Double.NaN
        val dxs = mutableListOf<Double>()
        var atrSmooth = 0.0; var dmPlusSmooth = 0.0; var dmMinusSmooth = 0.0
        for (i in 1 until candles.size) {
            val c = candles[i]; val p = candles[i - 1]
            val tr = max(c.high - c.low, max(abs(c.high - p.close), abs(c.low - p.close)))
            val up = c.high - p.high
            val down = p.low - c.low
            val dmPlus = if (up > down && up > 0) up else 0.0
            val dmMinus = if (down > up && down > 0) down else 0.0
            if (i <= period) {
                atrSmooth += tr; dmPlusSmooth += dmPlus; dmMinusSmooth += dmMinus
                if (i == period) { atrSmooth /= period; dmPlusSmooth /= period; dmMinusSmooth /= period }
            } else {
                atrSmooth = (atrSmooth * (period - 1) + tr) / period
                dmPlusSmooth = (dmPlusSmooth * (period - 1) + dmPlus) / period
                dmMinusSmooth = (dmMinusSmooth * (period - 1) + dmMinus) / period
            }
            if (i >= period && atrSmooth > 0) {
                val diPlus = 100 * dmPlusSmooth / atrSmooth
                val diMinus = 100 * dmMinusSmooth / atrSmooth
                val sum = diPlus + diMinus
                if (sum > 0) dxs.add(100 * abs(diPlus - diMinus) / sum)
            }
        }
        if (dxs.size < period) return if (dxs.isEmpty()) Double.NaN else dxs.average()
        return dxs.takeLast(period).average()
    }

    fun atr(candles: List<Candle>, period: Int = 14): Double {
        if (candles.size < period + 1) return Double.NaN
        val trs = mutableListOf<Double>()
        for (i in 1 until candles.size) {
            val c = candles[i]; val p = candles[i - 1]
            trs.add(max(c.high - c.low, max(abs(c.high - p.close), abs(c.low - p.close))))
        }
        var atr = trs.take(period).average()
        for (i in period until trs.size) atr = (atr * (period - 1) + trs[i]) / period
        return atr
    }

    fun obv(candles: List<Candle>): Double {
        var obv = 0.0
        for (i in 1 until candles.size) {
            val d = candles[i].close - candles[i - 1].close
            obv += when {
                d > 0 -> candles[i].volume
                d < 0 -> -candles[i].volume
                else -> 0.0
            }
        }
        return obv
    }

    data class Bollinger(val upper: Double, val middle: Double, val lower: Double, val width: Double)

    fun bollinger(closes: List<Double>, period: Int = 20, mult: Double = 2.0): Bollinger {
        val mid = sma(closes, period)
        if (mid.isNaN()) return Bollinger(Double.NaN, Double.NaN, Double.NaN, Double.NaN)
        val slice = closes.takeLast(period)
        val variance = slice.map { (it - mid) * (it - mid) }.average()
        val sd = sqrt(variance)
        val upper = mid + mult * sd
        val lower = mid - mult * sd
        return Bollinger(upper, mid, lower, if (mid != 0.0) (upper - lower) / mid else 0.0)
    }

    fun snapshot(candles: List<Candle>, timeframe: String): IndicatorSnapshot? {
        if (candles.size < 30) return null
        val closes = candles.map { it.close }
        val volumes = candles.map { it.volume }
        val price = closes.last()
        val r = rsi(closes)
        val m = macd(closes)
        val s20 = sma(closes, 20); val s50 = sma(closes, 50)
        val s200 = if (closes.size >= 200) sma(closes, 200) else s50
        val e20 = ema(closes, 20); val e50 = ema(closes, 50)
        val a = adx(candles)
        val b = bollinger(closes)
        val o = obv(candles)
        val atrV = atr(candles)
        val volSma = sma(volumes, 20)
        val volRatio = if (volSma > 0) volumes.last() / volSma else 1.0

        val trend = when {
            !s20.isNaN() && !s50.isNaN() && price > s20 && s20 > s50 -> Trend.UP
            !s20.isNaN() && !s50.isNaN() && price < s20 && s20 < s50 -> Trend.DOWN
            else -> Trend.SIDEWAYS
        }
        val momentum = when {
            !r.isNaN() && r > 55 && !m.histogram.isNaN() && m.histogram > 0 -> Momentum.BULLISH
            !r.isNaN() && r < 45 && !m.histogram.isNaN() && m.histogram < 0 -> Momentum.BEARISH
            else -> Momentum.NEUTRAL
        }
        return IndicatorSnapshot(
            timeframe, price, r, m.macd, m.signal, m.histogram,
            s20, s50, s200, e20, e50, a, b.upper, b.middle, b.lower, b.width,
            o, atrV, volSma, volRatio, trend, momentum
        )
    }
}
