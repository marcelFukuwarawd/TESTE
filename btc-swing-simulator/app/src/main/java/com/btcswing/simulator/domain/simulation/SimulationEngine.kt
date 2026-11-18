package com.btcswing.simulator.domain.simulation

import com.btcswing.simulator.domain.model.BotSettings
import kotlin.math.abs

/**
 * Motor de execução da SIMULAÇÃO. Funções puras — sem Android, sem rede,
 * totalmente cobertas por testes unitários.
 *
 * Regras:
 *  - Long-only spot (compra BTC com USDT fictício, vende de volta).
 *  - Slippage sempre CONTRA a operação: compra paga mais caro, venda recebe menos.
 *  - Taxa (fee) cobrada sobre o notional de cada lado.
 *  - PnL realizado da venda = proceeds líquidos − costBasis (que já inclui
 *    taxa e slippage da compra), ou seja, custos entram na conta.
 *  - Nenhuma operação é "inventada": se não há caixa, posição ou dados
 *    suficientes, a ordem é Rejected com motivo explícito.
 */
object SimulationEngine {

    /** Executa compra simulada. Retorna Executed ou Rejected (nunca lança). */
    fun buy(
        portfolio: Portfolio,
        marketPrice: Double,
        settings: BotSettings,
        time: Long,
        reason: String,
        score: Double,
        atrForStops: Double? = null
    ): ExecutionResult {
        if (portfolio.position != null) return ExecutionResult.Rejected("já existe posição aberta")
        if (!marketPrice.isFinite() || marketPrice <= 0) return ExecutionResult.Rejected("preço inválido/indisponível")

        val execPrice = applySlippage(marketPrice, Side.BUY, settings.slippageBps)
        val budget = portfolio.cashUsdt * (settings.positionSizePct / 100.0)
        // notional bruto tal que notional + fee caiba no orçamento
        val notional = budget / (1.0 + settings.feePct / 100.0)
        if (notional < settings.minNotionalUsdt) {
            return ExecutionResult.Rejected("caixa insuficiente para o notional mínimo (${fmt(settings.minNotionalUsdt)} USDT)")
        }
        val qty = notional / execPrice
        val fee = notional * (settings.feePct / 100.0)
        val slipCost = abs(execPrice - marketPrice) * qty
        val spent = notional + fee
        if (spent > portfolio.cashUsdt + 1e-9) return ExecutionResult.Rejected("caixa insuficiente")

        val stopLoss: Double
        val takeProfit: Double
        val atr = atrForStops?.takeIf { it.isFinite() && it > 0 }
        if (settings.useAtrStops && atr != null) {
            stopLoss = execPrice - settings.stopAtrMult * atr
            takeProfit = execPrice + settings.tpAtrMult * atr
        } else {
            stopLoss = execPrice * (1.0 - settings.stopLossPct / 100.0)
            takeProfit = execPrice * (1.0 + settings.takeProfitPct / 100.0)
        }

        val position = Position(
            qtyBtc = qty,
            entryPrice = execPrice,
            openedAt = time,
            costBasisUsdt = spent,
            stopLoss = stopLoss.coerceAtLeast(0.0),
            takeProfit = takeProfit
        )
        val newCash = portfolio.cashUsdt - spent
        val newPortfolio = Portfolio(cashUsdt = newCash, position = position)
        val trade = TradeRecord(
            time = time,
            side = Side.BUY,
            marketPrice = marketPrice,
            execPrice = execPrice,
            qtyBtc = qty,
            notionalUsdt = notional,
            feeUsdt = fee,
            slippageUsdt = slipCost,
            realizedPnlUsdt = null,
            realizedPnlPct = null,
            reason = reason,
            score = score,
            cashAfter = newCash,
            equityAfter = newPortfolio.equity(marketPrice),
            stopLoss = position.stopLoss,
            takeProfit = position.takeProfit
        )
        return ExecutionResult.Executed(trade, newPortfolio)
    }

    /** Executa venda simulada da posição atual. */
    fun sell(
        portfolio: Portfolio,
        marketPrice: Double,
        settings: BotSettings,
        time: Long,
        reason: String,
        score: Double
    ): ExecutionResult {
        val pos = portfolio.position ?: return ExecutionResult.Rejected("não há posição para vender")
        if (!marketPrice.isFinite() || marketPrice <= 0) return ExecutionResult.Rejected("preço inválido/indisponível")

        val execPrice = applySlippage(marketPrice, Side.SELL, settings.slippageBps)
        val gross = pos.qtyBtc * execPrice
        val fee = gross * (settings.feePct / 100.0)
        val proceeds = gross - fee
        val slipCost = abs(marketPrice - execPrice) * pos.qtyBtc
        val pnl = proceeds - pos.costBasisUsdt
        val pnlPct = if (pos.costBasisUsdt > 0) pnl / pos.costBasisUsdt * 100.0 else 0.0

        val newCash = portfolio.cashUsdt + proceeds
        val newPortfolio = Portfolio(cashUsdt = newCash, position = null)
        val trade = TradeRecord(
            time = time,
            side = Side.SELL,
            marketPrice = marketPrice,
            execPrice = execPrice,
            qtyBtc = pos.qtyBtc,
            notionalUsdt = gross,
            feeUsdt = fee,
            slippageUsdt = slipCost,
            realizedPnlUsdt = pnl,
            realizedPnlPct = pnlPct,
            reason = reason,
            score = score,
            cashAfter = newCash,
            equityAfter = newCash,
            stopLoss = null,
            takeProfit = null
        )
        return ExecutionResult.Executed(trade, newPortfolio)
    }

    /** Verifica stop-loss / take-profit; retorna o motivo do gatilho ou null. */
    fun checkStops(position: Position, currentPrice: Double): String? = when {
        currentPrice <= position.stopLoss -> "stop-loss atingido (preço ${fmt(currentPrice)} <= SL ${fmt(position.stopLoss)})"
        currentPrice >= position.takeProfit -> "take-profit atingido (preço ${fmt(currentPrice)} >= TP ${fmt(position.takeProfit)})"
        else -> null
    }

    /** Slippage adverso: compra executa acima do mercado, venda abaixo. */
    fun applySlippage(price: Double, side: Side, bps: Double): Double = when (side) {
        Side.BUY -> price * (1.0 + bps / 10_000.0)
        Side.SELL -> price * (1.0 - bps / 10_000.0)
    }

    private fun fmt(v: Double) = String.format(java.util.Locale.US, "%.2f", v)
}
