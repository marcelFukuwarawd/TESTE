package com.btcswing.simulator.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.btcswing.simulator.domain.model.BotSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "bot_settings")

/** Preferências do usuário (capital inicial, risco, horários…) via DataStore. */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val INITIAL_CAPITAL = doublePreferencesKey("initial_capital")
        val POSITION_SIZE = doublePreferencesKey("position_size_pct")
        val FEE = doublePreferencesKey("fee_pct")
        val SLIPPAGE = doublePreferencesKey("slippage_bps")
        val MIN_NOTIONAL = doublePreferencesKey("min_notional")
        val STOP_LOSS = doublePreferencesKey("stop_loss_pct")
        val TAKE_PROFIT = doublePreferencesKey("take_profit_pct")
        val USE_ATR = booleanPreferencesKey("use_atr_stops")
        val STOP_ATR_MULT = doublePreferencesKey("stop_atr_mult")
        val TP_ATR_MULT = doublePreferencesKey("tp_atr_mult")
        val BUY_THRESHOLD = doublePreferencesKey("buy_threshold")
        val SELL_THRESHOLD = doublePreferencesKey("sell_threshold")
        val INTERVAL_MIN = intPreferencesKey("interval_min")
        val COOLDOWN_MIN = intPreferencesKey("cooldown_min")
        val REPORT_HOUR = intPreferencesKey("report_hour")
        val BOT_ENABLED = booleanPreferencesKey("bot_enabled")
        val SYMBOL = stringPreferencesKey("symbol")
        val CAPITAL_CHANGED = booleanPreferencesKey("capital_changed")
    }

    val settings: Flow<BotSettings> = context.dataStore.data.map { p ->
        BotSettings(
            initialCapitalUsdt = p[Keys.INITIAL_CAPITAL] ?: 10_000.0,
            positionSizePct = p[Keys.POSITION_SIZE] ?: 25.0,
            feePct = p[Keys.FEE] ?: 0.10,
            slippageBps = p[Keys.SLIPPAGE] ?: 5.0,
            minNotionalUsdt = p[Keys.MIN_NOTIONAL] ?: 10.0,
            stopLossPct = p[Keys.STOP_LOSS] ?: 2.0,
            takeProfitPct = p[Keys.TAKE_PROFIT] ?: 4.0,
            useAtrStops = p[Keys.USE_ATR] ?: true,
            stopAtrMult = p[Keys.STOP_ATR_MULT] ?: 1.5,
            tpAtrMult = p[Keys.TP_ATR_MULT] ?: 3.0,
            buyThreshold = p[Keys.BUY_THRESHOLD] ?: 55.0,
            sellThreshold = p[Keys.SELL_THRESHOLD] ?: 55.0,
            analysisIntervalMinutes = p[Keys.INTERVAL_MIN] ?: 15,
            cooldownMinutes = p[Keys.COOLDOWN_MIN] ?: 60,
            dailyReportHour = p[Keys.REPORT_HOUR] ?: 8,
            botEnabled = p[Keys.BOT_ENABLED] ?: false,
            symbol = p[Keys.SYMBOL] ?: "BTCUSDT"
        ).sanitized()
    }

    suspend fun current(): BotSettings = settings.first()

    suspend fun update(transform: (BotSettings) -> BotSettings) {
        val next = transform(current()).sanitized()
        context.dataStore.edit { p ->
            p[Keys.INITIAL_CAPITAL] = next.initialCapitalUsdt
            p[Keys.POSITION_SIZE] = next.positionSizePct
            p[Keys.FEE] = next.feePct
            p[Keys.SLIPPAGE] = next.slippageBps
            p[Keys.MIN_NOTIONAL] = next.minNotionalUsdt
            p[Keys.STOP_LOSS] = next.stopLossPct
            p[Keys.TAKE_PROFIT] = next.takeProfitPct
            p[Keys.USE_ATR] = next.useAtrStops
            p[Keys.STOP_ATR_MULT] = next.stopAtrMult
            p[Keys.TP_ATR_MULT] = next.tpAtrMult
            p[Keys.BUY_THRESHOLD] = next.buyThreshold
            p[Keys.SELL_THRESHOLD] = next.sellThreshold
            p[Keys.INTERVAL_MIN] = next.analysisIntervalMinutes
            p[Keys.COOLDOWN_MIN] = next.cooldownMinutes
            p[Keys.REPORT_HOUR] = next.dailyReportHour
            p[Keys.BOT_ENABLED] = next.botEnabled
            p[Keys.SYMBOL] = next.symbol
        }
    }

    suspend fun setBotEnabled(enabled: Boolean) = update { it.copy(botEnabled = enabled) }
}
