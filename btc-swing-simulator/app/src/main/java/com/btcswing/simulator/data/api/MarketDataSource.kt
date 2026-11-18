package com.btcswing.simulator.data.api

import com.btcswing.simulator.domain.indicators.Candle
import com.btcswing.simulator.domain.strategy.Timeframe

/**
 * Fonte pública e gratuita de candles OHLCV. Sem chave de API, sem cadastro,
 * sem autenticação — apenas dados de mercado públicos.
 */
interface MarketDataSource {
    val name: String

    /**
     * Busca candles reais mais recentes.
     * @return lista em ordem cronológica crescente, ou null em falha.
     */
    suspend fun fetchCandles(symbol: String, timeframe: Timeframe, limit: Int): List<Candle>?
}
