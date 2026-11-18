package com.btcswing.simulator.data.api

import com.btcswing.simulator.data.db.CandleDao
import com.btcswing.simulator.data.db.toDomain
import com.btcswing.simulator.data.db.toEntity
import com.btcswing.simulator.domain.indicators.Candle
import com.btcswing.simulator.domain.indicators.CandleAggregator
import com.btcswing.simulator.domain.strategy.Timeframe

/** Origem dos candles usados no ciclo (para transparência no log/widget). */
data class CandlesResult(
    val timeframe: Timeframe,
    val candles: List<Candle>,
    val source: String,        // "Binance", "OKX", "cache"
    val stale: Boolean,        // true = veio do cache (rede falhou)
    val fetchedAt: Long
)

/**
 * Repositório de candles reais:
 *  1. Tenta Binance (vários hosts públicos).
 *  2. Falhou → tenta OKX.
 *  3. Falhou → usa o cache local (dados REAIS obtidos antes; marcados como stale).
 *  4. Nem cache → lista vazia (o robô fica em WAIT; nunca inventa candles).
 *
 * O timeframe de 10m é sintetizado localmente a partir dos candles de 5m
 * (a Binance/OKX não possuem barra nativa de 10 minutos).
 */
class MarketRepository(
    private val sources: List<MarketDataSource>,
    private val candleDao: CandleDao
) {
    /** Busca candles de um TF com fallback e cache. */
    suspend fun candles(symbol: String, tf: Timeframe, limit: Int = 300, now: Long): CandlesResult {
        if (tf.synthesizedFrom != null) {
            val base = candles(symbol, tf.synthesizedFrom, limit * 2, now)
            val synth = CandleAggregator.aggregate(base.candles, tf.synthesizedFrom.bucketMillis() * 2)
            return CandlesResult(tf, synth.takeLast(limit), base.source, base.stale, base.fetchedAt)
        }

        for (src in sources) {
            val fresh = try { src.fetchCandles(symbol, tf, limit) } catch (_: Exception) { null }
            if (!fresh.isNullOrEmpty()) {
                candleDao.upsertAll(fresh.map { it.toEntity(symbol, tf.label) })
                candleDao.prune(symbol, tf.label, fresh.first().openTime)
                return CandlesResult(tf, fresh, src.name, stale = false, fetchedAt = now)
            }
        }

        // Rede falhou em todas as fontes → cache local (dados reais antigos)
        val cached = candleDao.latest(symbol, tf.label, limit).map { it.toDomain() }.sortedBy { it.openTime }
        return CandlesResult(tf, cached, "cache", stale = true, fetchedAt = now)
    }

    /** Busca todos os timeframes de uma vez. */
    suspend fun allTimeframes(symbol: String, limit: Int = 300, now: Long): Map<Timeframe, CandlesResult> =
        Timeframe.entries.associateWith { candles(symbol, it, limit, now) }

}

/** Duração do bucket nativo do TF em ms (para síntese do 10m). */
fun Timeframe.bucketMillis(): Long = when (this) {
    Timeframe.M5 -> 5 * 60_000L
    Timeframe.M10 -> 10 * 60_000L
    Timeframe.M15 -> 15 * 60_000L
    Timeframe.H1 -> 60 * 60_000L
    Timeframe.H4 -> 4 * 60 * 60_000L
    Timeframe.D1 -> 24 * 60 * 60_000L
}
