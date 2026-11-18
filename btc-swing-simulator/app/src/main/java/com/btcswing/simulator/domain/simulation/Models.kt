package com.btcswing.simulator.domain.simulation

/** Lado da ordem simulada. */
enum class Side { BUY, SELL }

/** Posição aberta (long spot — o app não simula alavancagem nem short). */
data class Position(
    val qtyBtc: Double,
    val entryPrice: Double,
    val openedAt: Long,
    /** Custo total da posição incluindo taxa e slippage da compra. */
    val costBasisUsdt: Double,
    val stopLoss: Double,
    val takeProfit: Double
)

/** Estado completo da carteira simulada. */
data class Portfolio(
    val cashUsdt: Double,
    val position: Position? = null
) {
    /** Patrimônio = caixa + valor de mercado da posição a preço `price`. */
    fun equity(price: Double): Double = cashUsdt + (position?.qtyBtc ?: 0.0) * price

    /** Valor que sobraria se a posição fosse liquidada agora (com custos de saída). */
    fun liquidationValue(price: Double, feePct: Double, slippageBps: Double): Double {
        val p = position ?: return cashUsdt
        val execPrice = price * (1.0 - slippageBps / 10_000.0)
        val gross = p.qtyBtc * execPrice
        return cashUsdt + gross * (1.0 - feePct / 100.0)
    }
}

/** Registro imutável de uma operação simulada executada. */
data class TradeRecord(
    val time: Long,
    val side: Side,
    /** Preço de mercado no momento da decisão. */
    val marketPrice: Double,
    /** Preço realmente "executado" após slippage. */
    val execPrice: Double,
    val qtyBtc: Double,
    val notionalUsdt: Double,
    val feeUsdt: Double,
    val slippageUsdt: Double,
    /** Lucro/prejuízo realizado (apenas em vendas). */
    val realizedPnlUsdt: Double?,
    val realizedPnlPct: Double?,
    /** Motivo humano da operação (sinal, stop-loss, take-profit…). */
    val reason: String,
    /** Score agregado da decisão que gerou a operação. */
    val score: Double,
    val cashAfter: Double,
    val equityAfter: Double,
    val stopLoss: Double?,
    val takeProfit: Double?
)

/** Resultado da tentativa de execução de uma ordem. */
sealed class ExecutionResult {
    data class Executed(val trade: TradeRecord, val portfolio: Portfolio) : ExecutionResult()
    data class Rejected(val reason: String) : ExecutionResult()
}
