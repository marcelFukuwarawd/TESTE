package com.btcswing.simulator.domain.model

/**
 * Configurações do robô definidas pelo usuário. Modelo puro (sem Android),
 * persistido via DataStore e usado nos testes unitários.
 *
 * Dinheiro 100% fictício — nenhuma ordem real é enviada a corretora alguma.
 */
data class BotSettings(
    /** Capital inicial fictício em USDT definido pelo usuário. */
    val initialCapitalUsdt: Double = 10_000.0,
    /** Fração do patrimônio usada em cada compra (%). */
    val positionSizePct: Double = 25.0,
    /** Taxa de corretagem simulada por ordem (% do notional). Padrão spot Binance: 0,1%. */
    val feePct: Double = 0.10,
    /** Slippage simulado em basis points, sempre contra a operação. */
    val slippageBps: Double = 5.0,
    /** Notional mínimo da ordem em USDT (evita "poeira"). */
    val minNotionalUsdt: Double = 10.0,
    /** Stop-loss em % abaixo do preço de entrada (quando não usa ATR). */
    val stopLossPct: Double = 2.0,
    /** Take-profit em % acima do preço de entrada (quando não usa ATR). */
    val takeProfitPct: Double = 4.0,
    /** Se verdadeiro, SL/TP são calculados com ATR do 1h. */
    val useAtrStops: Boolean = true,
    /** Multiplicador de ATR para o stop-loss. */
    val stopAtrMult: Double = 1.5,
    /** Multiplicador de ATR para o take-profit. */
    val tpAtrMult: Double = 3.0,
    /** Score mínimo (0..100) para abrir compra. */
    val buyThreshold: Double = 55.0,
    /** Score mínimo (0..100, em módulo) para vender por sinal. */
    val sellThreshold: Double = 55.0,
    /** Intervalo entre ciclos de análise (minutos; mínimo 5). */
    val analysisIntervalMinutes: Int = 15,
    /** Cooldown entre operações (minutos) — evita overtrading. */
    val cooldownMinutes: Int = 60,
    /** Hora do relatório diário (0..23, fuso local do aparelho). */
    val dailyReportHour: Int = 8,
    /** Robô ligado/desligado. */
    val botEnabled: Boolean = false,
    /** Par analisado (fixo neste app). */
    val symbol: String = "BTCUSDT"
) {
    fun sanitized(): BotSettings {
        fun d(v: Double, def: Double, min: Double, max: Double) =
            (if (v.isFinite()) v else def).coerceIn(min, max)
        fun i(v: Int, def: Int, min: Int, max: Int) =
            (if (v in min..max) v else def).coerceIn(min, max)
        return copy(
            initialCapitalUsdt = d(initialCapitalUsdt, 10_000.0, 10.0, 100_000_000.0),
            positionSizePct = d(positionSizePct, 25.0, 1.0, 100.0),
            feePct = d(feePct, 0.10, 0.0, 1.0),
            slippageBps = d(slippageBps, 5.0, 0.0, 200.0),
            minNotionalUsdt = d(minNotionalUsdt, 10.0, 1.0, 10_000.0),
            stopLossPct = d(stopLossPct, 2.0, 0.1, 50.0),
            takeProfitPct = d(takeProfitPct, 4.0, 0.1, 200.0),
            stopAtrMult = d(stopAtrMult, 1.5, 0.1, 20.0),
            tpAtrMult = d(tpAtrMult, 3.0, 0.1, 40.0),
            buyThreshold = d(buyThreshold, 55.0, 1.0, 100.0),
            sellThreshold = d(sellThreshold, 55.0, 1.0, 100.0),
            analysisIntervalMinutes = i(analysisIntervalMinutes, 15, 5, 240),
            cooldownMinutes = i(cooldownMinutes, 60, 0, 1440),
            dailyReportHour = i(dailyReportHour, 8, 0, 23)
        )
    }
}
