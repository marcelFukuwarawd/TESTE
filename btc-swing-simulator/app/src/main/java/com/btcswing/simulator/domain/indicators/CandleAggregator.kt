package com.btcswing.simulator.domain.indicators

/**
 * Agrega candles menores em candles maiores (ex.: 2 x 5m → 1 x 10m).
 * Usado para o timeframe de 10 minutos, que as exchanges não fornecem
 * nativamente. Função pura — coberta por testes unitários.
 */
object CandleAggregator {

    /**
     * Agrupa por bucket de tempo alinhado à epoch.
     * open = primeiro, high = máximo, low = mínimo, close = último, volume = soma.
     */
    fun aggregate(candles: List<Candle>, bucketMillis: Long): List<Candle> {
        if (candles.isEmpty() || bucketMillis <= 0) return emptyList()
        return candles.sortedBy { it.openTime }
            .groupBy { it.openTime / bucketMillis }
            .toSortedMap()
            .values
            .map { group ->
                val sorted = group.sortedBy { it.openTime }
                Candle(
                    openTime = (sorted.first().openTime / bucketMillis) * bucketMillis,
                    open = sorted.first().open,
                    high = sorted.maxOf { it.high },
                    low = sorted.minOf { it.low },
                    close = sorted.last().close,
                    volume = sorted.sumOf { it.volume }
                )
            }
    }

    /** Converte candles de 5m em candles de 10m. */
    fun fiveToTenMinutes(candles5m: List<Candle>): List<Candle> =
        aggregate(candles5m, 10 * 60_000L)
}
