package com.btcswing.simulator.data.db

import com.btcswing.simulator.domain.indicators.Candle
import com.btcswing.simulator.domain.simulation.Portfolio
import com.btcswing.simulator.domain.simulation.Position
import com.btcswing.simulator.domain.simulation.Side
import com.btcswing.simulator.domain.simulation.TradeRecord

fun TradeRecord.toEntity(): TradeEntity = TradeEntity(
    time = time, side = side.name, marketPrice = marketPrice, execPrice = execPrice,
    qtyBtc = qtyBtc, notionalUsdt = notionalUsdt, feeUsdt = feeUsdt, slippageUsdt = slippageUsdt,
    realizedPnlUsdt = realizedPnlUsdt, realizedPnlPct = realizedPnlPct, reason = reason,
    score = score, cashAfter = cashAfter, equityAfter = equityAfter,
    stopLoss = stopLoss, takeProfit = takeProfit
)

fun TradeEntity.toDomain(): TradeRecord = TradeRecord(
    time = time, side = Side.valueOf(side), marketPrice = marketPrice, execPrice = execPrice,
    qtyBtc = qtyBtc, notionalUsdt = notionalUsdt, feeUsdt = feeUsdt, slippageUsdt = slippageUsdt,
    realizedPnlUsdt = realizedPnlUsdt, realizedPnlPct = realizedPnlPct, reason = reason,
    score = score, cashAfter = cashAfter, equityAfter = equityAfter,
    stopLoss = stopLoss, takeProfit = takeProfit
)

fun CandleEntity.toDomain(): Candle = Candle(openTime, open, high, low, close, volume)

fun Candle.toEntity(symbol: String, timeframe: String) =
    CandleEntity(symbol, timeframe, openTime, open, high, low, close, volume)

fun PortfolioEntity.toDomain(): Portfolio {
    val pos = if (posQtyBtc != null && posEntryPrice != null) Position(
        qtyBtc = posQtyBtc,
        entryPrice = posEntryPrice,
        openedAt = posOpenedAt ?: 0L,
        costBasisUsdt = posCostBasis ?: (posQtyBtc * posEntryPrice),
        stopLoss = posStopLoss ?: 0.0,
        takeProfit = posTakeProfit ?: Double.MAX_VALUE
    ) else null
    return Portfolio(cashUsdt = cashUsdt, position = pos)
}

