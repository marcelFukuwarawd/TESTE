package com.btcswing.simulator.data.api

import com.btcswing.simulator.domain.indicators.Candle
import com.btcswing.simulator.domain.strategy.Timeframe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit

/**
 * API pública spot da Binance (dados de mercado abertos, sem API key).
 * Tenta vários hosts oficiais em sequência (incluindo data-api.binance.vision,
 * espelho público de dados de mercado) para resistir a bloqueios regionais.
 */
class BinanceDataSource(private val client: OkHttpClient) : MarketDataSource {

    override val name = "Binance"

    private val hosts = listOf(
        "https://api.binance.com",
        "https://data-api.binance.vision",
        "https://api1.binance.com",
        "https://api2.binance.com",
        "https://api3.binance.com"
    )

    override suspend fun fetchCandles(symbol: String, timeframe: Timeframe, limit: Int): List<Candle>? =
        withContext(Dispatchers.IO) {
            if (timeframe.binanceInterval.isEmpty()) return@withContext null // sintetizado localmente
            val path = "/api/v3/klines?symbol=$symbol&interval=${timeframe.binanceInterval}&limit=$limit"
            for (host in hosts) {
                try {
                    val body = get("$host$path") ?: continue
                    return@withContext parseKlines(body)
                } catch (_: Exception) { /* tenta o próximo host */ }
            }
            null
        }

    private fun get(url: String): String? {
        val req = Request.Builder().url(url)
            .header("User-Agent", "BtcSwingSimulator/1.0")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            return resp.body?.string()
        }
    }

    /** Formato Binance: [[openTime, open, high, low, close, volume, closeTime, ...], ...] */
    internal fun parseKlines(json: String): List<Candle> {
        val arr = JSONArray(json)
        val out = ArrayList<Candle>(arr.length())
        for (i in 0 until arr.length()) {
            val k = arr.getJSONArray(i)
            out.add(
                Candle(
                    openTime = k.getLong(0),
                    open = k.getString(1).toDouble(),
                    high = k.getString(2).toDouble(),
                    low = k.getString(3).toDouble(),
                    close = k.getString(4).toDouble(),
                    volume = k.getString(5).toDouble(),
                    closeTime = k.optLong(6, k.getLong(0))
                )
            )
        }
        return out.sortedBy { it.openTime }
    }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
