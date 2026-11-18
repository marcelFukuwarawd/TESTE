package com.btcswing.simulator.data.api

import com.btcswing.simulator.domain.indicators.Candle
import com.btcswing.simulator.domain.strategy.Timeframe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Fonte RESERVA: API pública da OKX (também sem chave/cadastro).
 * Usada quando todos os hosts da Binance falham.
 * Formato: {"data":[[ts, o, h, l, c, vol, volCcy, ...], ...]} — mais recente primeiro.
 */
class OkxDataSource(private val client: OkHttpClient) : MarketDataSource {

    override val name = "OKX"

    private val host = "https://www.okx.com"

    override suspend fun fetchCandles(symbol: String, timeframe: Timeframe, limit: Int): List<Candle>? =
        withContext(Dispatchers.IO) {
            if (timeframe.okxBar.isEmpty()) return@withContext null // sintetizado localmente
            // BTCUSDT → BTC-USDT
            val instId = if (symbol.endsWith("USDT")) symbol.removeSuffix("USDT") + "-USDT" else symbol
            val url = "$host/api/v5/market/candles?instId=$instId&bar=${timeframe.okxBar}&limit=${limit.coerceAtMost(300)}"
            try {
                val req = Request.Builder().url(url)
                    .header("User-Agent", "BtcSwingSimulator/1.0")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext null
                    val body = resp.body?.string() ?: return@withContext null
                    parse(body)
                }
            } catch (_: Exception) {
                null
            }
        }

    internal fun parse(json: String): List<Candle>? {
        val obj = JSONObject(json)
        if (obj.optString("code") != "0") return null
        val arr = obj.getJSONArray("data")
        val out = ArrayList<Candle>(arr.length())
        for (i in 0 until arr.length()) {
            val k = arr.getJSONArray(i)
            out.add(
                Candle(
                    openTime = k.getString(0).toLong(),
                    open = k.getString(1).toDouble(),
                    high = k.getString(2).toDouble(),
                    low = k.getString(3).toDouble(),
                    close = k.getString(4).toDouble(),
                    volume = k.getString(5).toDouble()
                )
            )
        }
        return out.sortedBy { it.openTime }
    }
}
