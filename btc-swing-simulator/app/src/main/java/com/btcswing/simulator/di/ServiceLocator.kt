package com.btcswing.simulator.di

import android.content.Context
import com.btcswing.simulator.data.api.BinanceDataSource
import com.btcswing.simulator.data.api.MarketRepository
import com.btcswing.simulator.data.api.OkxDataSource
import com.btcswing.simulator.data.db.AppDatabase
import com.btcswing.simulator.data.prefs.SettingsRepository
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Injeção de dependência manual (sem Hilt/Koin) — simples, sem anotações,
 * sem risco extra de build no CI.
 */
object ServiceLocator {

    @Volatile private var initialized = false
    private lateinit var appContext: Context

    val database: AppDatabase by lazy { AppDatabase.get(appContext) }
    val settings: SettingsRepository by lazy { SettingsRepository(appContext) }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    val marketRepository: MarketRepository by lazy {
        MarketRepository(
            sources = listOf(
                BinanceDataSource(httpClient),   // primária (hosts públicos)
                OkxDataSource(httpClient)        // reserva
            ),
            candleDao = database.candleDao()
        )
    }

    val tradingEngine: com.btcswing.simulator.service.TradingEngine by lazy {
        com.btcswing.simulator.service.TradingEngine(
            marketRepository = marketRepository,
            settingsRepository = settings,
            db = database
        )
    }

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            appContext = context.applicationContext
            initialized = true
        }
    }
}
