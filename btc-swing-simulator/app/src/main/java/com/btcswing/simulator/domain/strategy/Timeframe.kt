package com.btcswing.simulator.domain.strategy

/**
 * Timeframes analisados pelo robô.
 *
 * Obs.: a Binance não oferece candles nativos de 10 minutos. O timeframe M10
 * é SINTETIZADO localmente agrupando candles de 5m (2 x 5m = 10m). Isso é
 * documentado em docs/FONTES_DE_DADOS.md.
 */
enum class Timeframe(
    val label: String,
    /** Intervalo na API pública da Binance (vazio = sintetizado localmente). */
    val binanceInterval: String,
    /** Intervalo na API pública da OKX (fonte reserva). */
    val okxBar: String,
    /** Peso do timeframe na decisão agregada (timeframes maiores pesam mais). */
    val weight: Double,
    /** Se não for nulo, os candles deste TF são sintetizados a partir dele. */
    val synthesizedFrom: Timeframe? = null
) {
    M5("5m", "5m", "5m", 0.10),
    M10("10m", "", "", 0.10, synthesizedFrom = M5),
    M15("15m", "15m", "15m", 0.15),
    H1("1h", "1h", "1H", 0.20),
    H4("4h", "4h", "4H", 0.20),
    D1("1d", "1d", "1D", 0.25);

    companion object {
        fun fromLabel(label: String): Timeframe? = entries.firstOrNull { it.label == label }
    }
}
