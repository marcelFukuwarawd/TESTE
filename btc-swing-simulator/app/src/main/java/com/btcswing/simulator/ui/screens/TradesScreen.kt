package com.btcswing.simulator.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.btcswing.simulator.MainViewModel
import com.btcswing.simulator.domain.simulation.Side
import com.btcswing.simulator.ui.components.Green
import com.btcswing.simulator.ui.components.Muted
import com.btcswing.simulator.ui.components.Red
import com.btcswing.simulator.ui.components.btc
import com.btcswing.simulator.ui.components.fmtTime
import com.btcswing.simulator.ui.components.pnlColor
import com.btcswing.simulator.ui.components.usdt
import java.util.Locale

@Composable
fun TradesScreen(vm: MainViewModel) {
    val state by vm.uiState.collectAsState()
    val trades = state.trades

    if (trades.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text("Nenhuma operação ainda", fontWeight = FontWeight.Bold)
            Text(
                "Ligue o robô na aba Início. Todas as compras e vendas simuladas aparecerão aqui com taxas e slippage.",
                color = Muted
            )
        }
        return
    }

    val realized = trades.filter { it.side == Side.SELL.name }
        .sumOf { it.realizedPnlUsdt ?: 0.0 }

    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                "Histórico de operações (simuladas) • PnL realizado: ${String.format(Locale.US, "%+,.2f", realized)} USDT",
                color = pnlColor(realized), fontWeight = FontWeight.SemiBold
            )
        }
        items(trades, key = { it.id }) { t ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(12.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            if (t.side == Side.BUY.name) "🟢 COMPRA" else "🔴 VENDA",
                            color = if (t.side == Side.BUY.name) Green else Red,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.weight(1f))
                        Text(fmtTime(t.time), color = Muted, fontSize = MaterialTheme.typography.bodySmall.fontSize)
                    }
                    Text("${btc(t.qtyBtc)} BTC @ $${usdt(t.execPrice)} (mercado: $${usdt(t.marketPrice)})", fontSize = MaterialTheme.typography.bodyMedium.fontSize)
                    Text(
                        "Notional ${usdt(t.notionalUsdt)} • taxa ${usdt(t.feeUsdt)} • slippage ${usdt(t.slippageUsdt)}",
                        color = Muted, fontSize = MaterialTheme.typography.bodySmall.fontSize
                    )
                    if (t.realizedPnlUsdt != null) {
                        Text(
                            "PnL realizado: ${String.format(Locale.US, "%+,.2f", t.realizedPnlUsdt)} USDT (${String.format(Locale.US, "%+.2f%%", t.realizedPnlPct ?: 0.0)})",
                            color = pnlColor(t.realizedPnlUsdt ?: 0.0), fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (t.stopLoss != null) Text("SL $${usdt(t.stopLoss)} • TP $${usdt(t.takeProfit ?: 0.0)}", color = Muted, fontSize = MaterialTheme.typography.bodySmall.fontSize)
                    Text("Motivo: ${t.reason}", color = Muted, fontSize = MaterialTheme.typography.bodySmall.fontSize)
                }
            }
        }
    }
}
