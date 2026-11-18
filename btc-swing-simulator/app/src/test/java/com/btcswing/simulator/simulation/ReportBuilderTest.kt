package com.btcswing.simulator.simulation

import com.btcswing.simulator.domain.model.BotSettings
import com.btcswing.simulator.domain.simulation.EquityPoint
import com.btcswing.simulator.domain.simulation.ExecutionResult
import com.btcswing.simulator.domain.simulation.Portfolio
import com.btcswing.simulator.domain.simulation.ReportBuilder
import com.btcswing.simulator.domain.simulation.SimulationEngine
import com.btcswing.simulator.domain.simulation.TradeRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportBuilderTest {

    private val settings = BotSettings(initialCapitalUsdt = 10_000.0, useAtrStops = false)
    private val t0 = 1_700_000_000_000L
    private val day = 24L * 60 * 60 * 1000

    /** Compra em t0 a 50k e venda em t0+1h a 51k → 1 venda vencedora. */
    private fun roundTrip(): Pair<List<TradeRecord>, Portfolio> {
        val buy = SimulationEngine.buy(Portfolio(10_000.0), 50_000.0, settings, t0, "sinal", 60.0) as ExecutionResult.Executed
        val sell = SimulationEngine.sell(buy.portfolio, 51_000.0, settings, t0 + 3_600_000, "alvo", -60.0) as ExecutionResult.Executed
        return listOf(buy.trade, sell.trade) to sell.portfolio
    }

    @Test fun `relatorio com uma operacao vencedora`() {
        val (trades, portfolio) = roundTrip()
        val report = ReportBuilder.build(
            tradesInWindow = trades,
            equityPointsInWindow = listOf(EquityPoint(t0, 10_000.0), EquityPoint(t0 + day, portfolio.cashUsdt)),
            initialCapitalUsdt = 10_000.0,
            currentPortfolio = portfolio,
            currentPrice = 51_000.0,
            windowStart = t0,
            windowEnd = t0 + day
        )
        assertEquals(2, report.tradeCount)
        assertEquals(1, report.winCount)
        assertEquals(0, report.lossCount)
        assertTrue(report.realizedPnlUsdt > 0)
        assertNotNull(report.bestTradePnlUsdt)
        assertEquals(report.realizedPnlUsdt, report.bestTradePnlUsdt!!, 1e-9)
        assertEquals(portfolio.cashUsdt, report.finalBalanceUsdt, 1e-9)
        assertTrue(report.cumulativePnlUsdt > 0)
        assertTrue(report.summaryLines.isNotEmpty())
    }

    @Test fun `relatorio sem operacoes diz explicitamente que ficou em espera`() {
        val report = ReportBuilder.build(
            tradesInWindow = emptyList(),
            equityPointsInWindow = listOf(EquityPoint(t0, 10_000.0)),
            initialCapitalUsdt = 10_000.0,
            currentPortfolio = Portfolio(10_000.0),
            currentPrice = 50_000.0,
            windowStart = t0,
            windowEnd = t0 + day
        )
        assertEquals(0, report.tradeCount)
        assertEquals(0.0, report.realizedPnlUsdt, 1e-9)
        assertNull(report.bestTradePnlUsdt)
        assertTrue(report.summaryLines.any { "Nenhuma operação" in it })
    }

    @Test fun `perda e registrada com sinal negativo`() {
        val buy = SimulationEngine.buy(Portfolio(10_000.0), 50_000.0, settings, t0, "sinal", 60.0) as ExecutionResult.Executed
        val sell = SimulationEngine.sell(buy.portfolio, 47_000.0, settings, t0 + 3_600_000, "stop", -60.0) as ExecutionResult.Executed
        val report = ReportBuilder.build(
            listOf(buy.trade, sell.trade),
            listOf(EquityPoint(t0, 10_000.0), EquityPoint(t0 + day, sell.portfolio.cashUsdt)),
            10_000.0, sell.portfolio, 47_000.0, t0, t0 + day
        )
        assertEquals(1, report.lossCount)
        assertTrue(report.realizedPnlUsdt < 0)
        assertTrue(report.realizedPnlPct < 0)
        assertTrue(report.cumulativePnlUsdt < 0)
    }

    @Test fun `drawdown maximo calculado sobre a curva`() {
        val points = listOf(
            EquityPoint(t0, 10_000.0),
            EquityPoint(t0 + 1000, 12_000.0), // pico
            EquityPoint(t0 + 2000, 9_000.0),  // vale → dd = 25%
            EquityPoint(t0 + 3000, 11_000.0)
        )
        assertEquals(25.0, ReportBuilder.maxDrawdownPct(points), 1e-9)
        assertEquals(0.0, ReportBuilder.maxDrawdownPct(listOf(EquityPoint(t0, 1.0))), 1e-9)
    }
}
