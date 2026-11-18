package com.btcswing.simulator.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Operação simulada persistida (espelho de TradeRecord). */
@Entity(tableName = "trades")
data class TradeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: Long,
    val side: String,            // BUY | SELL
    val marketPrice: Double,
    val execPrice: Double,
    val qtyBtc: Double,
    val notionalUsdt: Double,
    val feeUsdt: Double,
    val slippageUsdt: Double,
    val realizedPnlUsdt: Double?,
    val realizedPnlPct: Double?,
    val reason: String,
    val score: Double,
    val cashAfter: Double,
    val equityAfter: Double,
    val stopLoss: Double?,
    val takeProfit: Double?
)

/** Log de TODAS as decisões (BUY/SELL/WAIT) com justificativas. */
@Entity(tableName = "decisions")
data class DecisionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: Long,
    val action: String,
    val score: Double,
    val price: Double,
    val reasons: String,        // linhas separadas por \n
    val scoresByTf: String,     // JSON simples {"1h":12.3,...}
    val executed: Boolean       // gerou operação?
)

/** Cache local de candles reais (sobrevive a reinicialização do aparelho). */
@Entity(tableName = "candles", primaryKeys = ["symbol", "timeframe", "openTime"])
data class CandleEntity(
    val symbol: String,
    val timeframe: String,
    val openTime: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double
)

/** Estado da carteira simulada — linha única (id=1). */
@Entity(tableName = "portfolio")
data class PortfolioEntity(
    @PrimaryKey val id: Int = 1,
    val cashUsdt: Double,
    val initialCapitalUsdt: Double,
    // posição aberta (null qty = sem posição)
    val posQtyBtc: Double?,
    val posEntryPrice: Double?,
    val posOpenedAt: Long?,
    val posCostBasis: Double?,
    val posStopLoss: Double?,
    val posTakeProfit: Double?,
    val lastTradeTime: Long?,
    val lastPrice: Double?,
    val lastUpdatedAt: Long?
)

/** Pontos de patrimônio para curva de desempenho/drawdown. */
@Entity(tableName = "equity_points")
data class EquityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: Long,
    val equityUsdt: Double,
    val cashUsdt: Double,
    val price: Double
)

/** Relatório diário persistido. */
@Entity(tableName = "reports")
data class ReportEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val generatedAt: Long,
    val windowStart: Long,
    val windowEnd: Long,
    val tradeCount: Int,
    val winCount: Int,
    val lossCount: Int,
    val realizedPnlUsdt: Double,
    val realizedPnlPct: Double,
    val finalBalanceUsdt: Double,
    val finalEquityUsdt: Double,
    val cumulativePnlUsdt: Double,
    val cumulativePnlPct: Double,
    val maxDrawdownPct: Double,
    val bestTradePnlUsdt: Double?,
    val worstTradePnlUsdt: Double?,
    val summaryLines: String   // separadas por \n
)
