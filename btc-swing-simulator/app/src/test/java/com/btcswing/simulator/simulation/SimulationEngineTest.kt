package com.btcswing.simulator.simulation

import com.btcswing.simulator.domain.model.BotSettings
import com.btcswing.simulator.domain.simulation.ExecutionResult
import com.btcswing.simulator.domain.simulation.Portfolio
import com.btcswing.simulator.domain.simulation.Side
import com.btcswing.simulator.domain.simulation.SimulationEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SimulationEngineTest {

    private val settings = BotSettings(
        initialCapitalUsdt = 10_000.0,
        positionSizePct = 25.0,
        feePct = 0.10,
        slippageBps = 5.0,
        minNotionalUsdt = 10.0,
        useAtrStops = false,
        stopLossPct = 2.0,
        takeProfitPct = 4.0
    )

    private val portfolio = Portfolio(cashUsdt = 10_000.0)
    private val price = 50_000.0

    @Test fun `compra aplica slippage contra e cobra taxa`() {
        val r = SimulationEngine.buy(portfolio, price, settings, time = 1000, reason = "teste", score = 60.0)
        assertTrue(r is ExecutionResult.Executed)
        val exec = (r as ExecutionResult.Executed)
        val expectedExecPrice = price * (1 + 5.0 / 10_000)
        assertEquals(expectedExecPrice, exec.trade.execPrice, 1e-9)
        // budget = 25% de 10k = 2500; notional = 2500 / 1.001 ≈ 2497.50; fee ≈ 2.4975
        val expectedNotional = 2500.0 / (1 + 0.001)
        assertEquals(expectedNotional, exec.trade.notionalUsdt, 1e-6)
        assertEquals(expectedNotional * 0.001, exec.trade.feeUsdt, 1e-6)
        assertEquals(expectedNotional / expectedExecPrice, exec.trade.qtyBtc, 1e-12)
        // caixa final = 10000 − (notional + fee) = 7500
        assertEquals(7_500.0, exec.portfolio.cashUsdt, 1e-6)
        assertNotNull(exec.portfolio.position)
        assertNull(exec.trade.realizedPnlUsdt) // compra não realiza PnL
    }

    @Test fun `stop e take-profit percentuais corretos`() {
        val exec = SimulationEngine.buy(portfolio, price, settings, 1000, "t", 60.0) as ExecutionResult.Executed
        val pos = exec.portfolio.position!!
        assertEquals(pos.entryPrice * 0.98, pos.stopLoss, 1e-9)
        assertEquals(pos.entryPrice * 1.04, pos.takeProfit, 1e-9)
    }

    @Test fun `stop e take-profit por ATR quando habilitado`() {
        val atrSettings = settings.copy(useAtrStops = true, stopAtrMult = 1.5, tpAtrMult = 3.0)
        val exec = SimulationEngine.buy(portfolio, price, atrSettings, 1000, "t", 60.0, atrForStops = 500.0) as ExecutionResult.Executed
        val pos = exec.portfolio.position!!
        assertEquals(pos.entryPrice - 1.5 * 500.0, pos.stopLoss, 1e-9)
        assertEquals(pos.entryPrice + 3.0 * 500.0, pos.takeProfit, 1e-9)
    }

    @Test fun `venda realiza PnL liquido de taxas e slippage dos dois lados`() {
        val buy = SimulationEngine.buy(portfolio, price, settings, 1000, "t", 60.0) as ExecutionResult.Executed
        val sellPrice = 51_000.0
        val sell = SimulationEngine.sell(buy.portfolio, sellPrice, settings, 2000, "t", -60.0) as ExecutionResult.Executed
        val pos = buy.portfolio.position!!
        val execSell = sellPrice * (1 - 5.0 / 10_000)
        val gross = pos.qtyBtc * execSell
        val proceeds = gross * (1 - 0.001)
        val expectedPnl = proceeds - pos.costBasisUsdt
        assertEquals(expectedPnl, sell.trade.realizedPnlUsdt!!, 1e-6)
        assertTrue(sell.trade.realizedPnlUsdt!! > 0) // preço subiu ~2%
        assertEquals(10_000.0 - pos.costBasisUsdt + proceeds, sell.portfolio.cashUsdt, 1e-6)
        assertNull(sell.portfolio.position)
        assertEquals(Side.SELL, sell.trade.side)
    }

    @Test fun `venda com prejuizo registra PnL negativo`() {
        val buy = SimulationEngine.buy(portfolio, price, settings, 1000, "t", 60.0) as ExecutionResult.Executed
        val sell = SimulationEngine.sell(buy.portfolio, 48_000.0, settings, 2000, "stop", -60.0) as ExecutionResult.Executed
        assertTrue(sell.trade.realizedPnlUsdt!! < 0)
        assertTrue(sell.trade.realizedPnlPct!! < 0)
    }

    @Test fun `nao compra duas vezes com posicao aberta`() {
        val buy1 = SimulationEngine.buy(portfolio, price, settings, 1000, "t", 60.0) as ExecutionResult.Executed
        val buy2 = SimulationEngine.buy(buy1.portfolio, price, settings, 1100, "t", 60.0)
        assertTrue(buy2 is ExecutionResult.Rejected)
        assertTrue((buy2 as ExecutionResult.Rejected).reason.contains("posição"))
    }

    @Test fun `nao vende sem posicao`() {
        val r = SimulationEngine.sell(portfolio, price, settings, 1000, "t", -60.0)
        assertTrue(r is ExecutionResult.Rejected)
    }

    @Test fun `preco invalido nunca gera operacao inventada`() {
        assertTrue(SimulationEngine.buy(portfolio, Double.NaN, settings, 1, "t", 99.0) is ExecutionResult.Rejected)
        assertTrue(SimulationEngine.buy(portfolio, -5.0, settings, 1, "t", 99.0) is ExecutionResult.Rejected)
        assertTrue(SimulationEngine.buy(portfolio, 0.0, settings, 1, "t", 99.0) is ExecutionResult.Rejected)
    }

    @Test fun `caixa pequeno abaixo do notional minimo e rejeitado`() {
        val small = Portfolio(cashUsdt = 20.0) // 25% = 5 < 10 mínimo
        val r = SimulationEngine.buy(small, price, settings, 1000, "t", 60.0)
        assertTrue(r is ExecutionResult.Rejected)
        assertTrue((r as ExecutionResult.Rejected).reason.contains("mínimo"))
    }

    @Test fun `checkStops dispara stop-loss e take-profit`() {
        val buy = SimulationEngine.buy(portfolio, price, settings, 1000, "t", 60.0) as ExecutionResult.Executed
        val pos = buy.portfolio.position!!
        assertNotNull(SimulationEngine.checkStops(pos, pos.stopLoss - 1))
        assertTrue(SimulationEngine.checkStops(pos, pos.stopLoss - 1)!!.contains("stop-loss"))
        assertNotNull(SimulationEngine.checkStops(pos, pos.takeProfit + 1))
        assertTrue(SimulationEngine.checkStops(pos, pos.takeProfit + 1)!!.contains("take-profit"))
        assertNull(SimulationEngine.checkStops(pos, price * 1.001))
    }

    @Test fun `equity marca posicao a mercado`() {
        val buy = SimulationEngine.buy(portfolio, price, settings, 1000, "t", 60.0) as ExecutionResult.Executed
        val eq = buy.portfolio.equity(price)
        // equity ≈ 10000 − fee − custo de slippage (pequena diferença)
        assertTrue(eq < 10_000.0 && eq > 9_990.0)
        // se o preço sobe, equity sobe
        assertTrue(buy.portfolio.equity(price * 1.01) > eq)
    }
}
