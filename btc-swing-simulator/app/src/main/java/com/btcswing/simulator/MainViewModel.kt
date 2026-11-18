package com.btcswing.simulator

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.btcswing.simulator.data.db.DecisionEntity
import com.btcswing.simulator.data.db.EquityEntity
import com.btcswing.simulator.data.db.PortfolioEntity
import com.btcswing.simulator.data.db.ReportEntity
import com.btcswing.simulator.data.db.TradeEntity
import com.btcswing.simulator.data.db.toDomain
import com.btcswing.simulator.di.ServiceLocator
import com.btcswing.simulator.domain.model.BotSettings
import com.btcswing.simulator.domain.simulation.Portfolio
import com.btcswing.simulator.service.BotController
import com.btcswing.simulator.service.TradingForegroundService
import com.btcswing.simulator.widget.BtcWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Estado agregado da UI. */
data class UiState(
    val settings: BotSettings = BotSettings(),
    val portfolio: Portfolio = Portfolio(cashUsdt = 0.0),
    val initialCapital: Double = 0.0,
    val lastPrice: Double = 0.0,
    val equity: Double = 0.0,
    val pnlUsdt: Double = 0.0,
    val pnlPct: Double = 0.0,
    val lastUpdatedAt: Long? = null,
    val trades: List<TradeEntity> = emptyList(),
    val decisions: List<DecisionEntity> = emptyList(),
    val reports: List<ReportEntity> = emptyList(),
    val equityCurve: List<EquityEntity> = emptyList()
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val db = ServiceLocator.database
    private val settingsRepo = ServiceLocator.settings

    /** Serviço em primeiro plano ativo? */
    val botRunning: MutableStateFlow<Boolean> = MutableStateFlow(TradingForegroundService.isRunning)

    /** Status do último ciclo manual (para feedback na tela). */
    val manualCycleStatus: MutableStateFlow<String?> = MutableStateFlow(null)
    val busy: MutableStateFlow<Boolean> = MutableStateFlow(false)

    // combine suporta até 5 flows tipados — aninhamos dois grupos
    private data class GroupA(
        val settings: BotSettings,
        val portfolioRow: PortfolioEntity?,
        val trades: List<TradeEntity>
    )

    private data class GroupB(
        val decisions: List<DecisionEntity>,
        val reports: List<ReportEntity>,
        val equityCurve: List<EquityEntity>
    )

    val uiState: StateFlow<UiState> = combine(
        combine(
            settingsRepo.settings,
            db.portfolioDao().observe(),
            db.tradeDao().observeRecent(100)
        ) { settings, portfolioRow, trades -> GroupA(settings, portfolioRow, trades) },
        combine(
            db.decisionDao().observeRecent(50),
            db.reportDao().observeRecent(30),
            db.equityDao().observeRecent(200)
        ) { decisions, reports, equityCurve -> GroupB(decisions, reports, equityCurve) }
    ) { a, b ->
        buildState(a.settings, a.portfolioRow, a.trades, b.decisions, b.reports, b.equityCurve)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    private fun buildState(
        settings: BotSettings,
        row: PortfolioEntity?,
        trades: List<TradeEntity>,
        decisions: List<DecisionEntity>,
        reports: List<ReportEntity>,
        equityCurve: List<EquityEntity>
    ): UiState {
        val portfolio = row?.toDomain() ?: Portfolio(cashUsdt = settings.initialCapitalUsdt)
        val initial = row?.initialCapitalUsdt ?: settings.initialCapitalUsdt
        val price = row?.lastPrice ?: 0.0
        val equity = if (price > 0) portfolio.equity(price) else portfolio.cashUsdt
        val pnl = equity - initial
        return UiState(
            settings = settings,
            portfolio = portfolio,
            initialCapital = initial,
            lastPrice = price,
            equity = equity,
            pnlUsdt = pnl,
            pnlPct = if (initial > 0) pnl / initial * 100.0 else 0.0,
            lastUpdatedAt = row?.lastUpdatedAt,
            trades = trades,
            decisions = decisions,
            reports = reports,
            equityCurve = equityCurve.sortedBy { it.time }
        )
    }

    // ------------------------------------------------------------- ações

    fun setBotEnabled(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (enabled) BotController.enable(getApplication()) else BotController.disable(getApplication())
                botRunning.value = TradingForegroundService.isRunning
                BtcWidgetProvider.refreshAll(getApplication())
            } catch (t: Throwable) {
                manualCycleStatus.value = "Erro ao ${if (enabled) "ligar" else "desligar"}: ${t.message}"
            }
        }
    }

    /** Roda um ciclo de análise imediatamente (sem esperar o intervalo). */
    fun runCycleNow() {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val outcome = ServiceLocator.tradingEngine.runCycle()
                manualCycleStatus.value = outcome.message
                botRunning.value = TradingForegroundService.isRunning
                BtcWidgetProvider.refreshAll(getApplication())
            } catch (t: Throwable) {
                manualCycleStatus.value = "Erro no ciclo: ${t.message}"
            } finally {
                busy.value = false
            }
        }
    }

    fun saveSettings(newSettings: BotSettings) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.update { newSettings.sanitized() }
            // horário do relatório mudou → reagenda
            BotController.scheduleWorkers(getApplication(), newSettings.sanitized())
            manualCycleStatus.value = "Configurações salvas."
        }
    }

    /** Zera histórico e reinicia a simulação com novo capital fictício. */
    fun resetSimulation(initialCapital: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val fallback = settingsRepo.current().initialCapitalUsdt
                val capital = if (initialCapital.isFinite() && initialCapital >= 10.0) initialCapital else fallback
                ServiceLocator.tradingEngine.resetSimulation(capital)
                settingsRepo.update { it.copy(initialCapitalUsdt = capital) }
                manualCycleStatus.value = "Simulação reiniciada com $capital USDT fictícios."
                BtcWidgetProvider.refreshAll(getApplication())
            } catch (t: Throwable) {
                manualCycleStatus.value = "Erro ao reiniciar: ${t.message}"
            }
        }
    }

    fun refreshRunningState() {
        botRunning.value = TradingForegroundService.isRunning
    }
}
