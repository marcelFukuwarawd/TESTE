package com.btcswing.simulator.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.btcswing.simulator.MainViewModel
import com.btcswing.simulator.domain.simulation.Side
import com.btcswing.simulator.ui.components.Green
import com.btcswing.simulator.ui.components.Muted
import com.btcswing.simulator.ui.components.Orange
import com.btcswing.simulator.ui.components.Sparkline
import com.btcswing.simulator.ui.components.StatCard
import com.btcswing.simulator.ui.components.TwoStats
import com.btcswing.simulator.ui.components.fmtTime
import com.btcswing.simulator.ui.components.pnlColor
import com.btcswing.simulator.ui.components.usdt
import java.util.Locale

@Composable
fun DashboardScreen(vm: MainViewModel) {
    val state by vm.uiState.collectAsState()
    val running by vm.botRunning.collectAsState()
    val busy by vm.busy.collectAsState()
    val manualStatus by vm.manualCycleStatus.collectAsState()
    val s = state.settings
    val pos = state.portfolio.position

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Status do robô
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Robô de swing trade", fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.titleMedium.fontSize)
                    Text(
                        if (running) "Analisando BTC/USDT a cada ${s.analysisIntervalMinutes} min"
                        else "Parado — ligue para analisar automaticamente",
                        color = Muted, fontSize = MaterialTheme.typography.bodySmall.fontSize
                    )
                }
                Switch(checked = running, onCheckedChange = { vm.setBotEnabled(it) })
            }
        }

        // Preço + patrimônio
        TwoStats(
            left = { StatCard("Preço BTC (real)", if (state.lastPrice > 0) "$" + usdt(state.lastPrice) else "aguardando dados…", valueColor = Orange, modifier = Modifier.fillMaxWidth()) },
            right = { StatCard("Patrimônio (fictício)", usdt(state.equity) + " USDT", valueColor = Color.White, modifier = Modifier.fillMaxWidth()) }
        )
        TwoStats(
            left = { StatCard("Saldo em caixa", usdt(state.portfolio.cashUsdt) + " USDT", modifier = Modifier.fillMaxWidth()) },
            right = {
                StatCard(
                    "Lucro/Prejuízo",
                    String.format(Locale.US, "%+,.2f", state.pnlUsdt) + " USDT",
                    pnlColor(state.pnlUsdt),
                    "Capital inicial: ${usdt(state.initialCapital)} • ${String.format(Locale.US, "%+.2f%%", state.pnlPct)}",
                    Modifier.fillMaxWidth()
                )
            }
        )

        // Posição aberta
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(14.dp)) {
                Text("Posição", color = Muted, fontSize = MaterialTheme.typography.labelMedium.fontSize)
                if (pos != null) {
                    val upnl = pos.qtyBtc * state.lastPrice - pos.costBasisUsdt
                    Text(
                        "${String.format(Locale.US, "%.6f", pos.qtyBtc)} BTC @ $${usdt(pos.entryPrice)}",
                        fontWeight = FontWeight.SemiBold
                    )
                    Text("SL $${usdt(pos.stopLoss)} • TP $${usdt(pos.takeProfit)}", color = Muted, fontSize = MaterialTheme.typography.bodySmall.fontSize)
                    if (state.lastPrice > 0) Text("PnL aberto: ${String.format(Locale.US, "%+,.2f", upnl)} USDT", color = pnlColor(upnl), fontSize = MaterialTheme.typography.bodySmall.fontSize)
                } else {
                    Text("Nenhuma posição aberta", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Curva de patrimônio
        if (state.equityCurve.size >= 2) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(14.dp)) {
                    Text("Curva de patrimônio", color = Muted, fontSize = MaterialTheme.typography.labelMedium.fontSize)
                    Spacer(Modifier.height(6.dp))
                    Sparkline(state.equityCurve.map { it.equityUsdt }, state.pnlUsdt >= 0)
                }
            }
        }

        // Última decisão
        state.decisions.firstOrNull()?.let { d ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(14.dp)) {
                    Text("Última decisão (${fmtTime(d.time)})", color = Muted, fontSize = MaterialTheme.typography.labelMedium.fontSize)
                    val action = when (d.action) { "BUY" -> "COMPRA"; "SELL" -> "VENDA"; else -> "ESPERA" }
                    Text("$action • score ${String.format(Locale.US, "%.1f", d.score)}", fontWeight = FontWeight.Bold, color = when (d.action) { "BUY" -> Green; "SELL" -> com.btcswing.simulator.ui.components.Red; else -> Color.White })
                    d.reasons.split("\n").drop(1).take(7).forEach {
                        Text(it, fontSize = MaterialTheme.typography.bodySmall.fontSize, color = Muted)
                    }
                }
            }
        }

        // Últimas operações
        state.trades.take(5).let { recent ->
            if (recent.isNotEmpty()) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Últimas operações", color = Muted, fontSize = MaterialTheme.typography.labelMedium.fontSize)
                        recent.forEach { t ->
                            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                                Text(
                                    if (t.side == Side.BUY.name) "🟢" else "🔴",
                                    Modifier.padding(end = 6.dp)
                                )
                                Column(Modifier.weight(1f)) {
                                    Text("${fmtTime(t.time)} • ${String.format(Locale.US, "%.5f", t.qtyBtc)} BTC @ $${usdt(t.execPrice)}", fontSize = MaterialTheme.typography.bodySmall.fontSize)
                                    t.realizedPnlUsdt?.let {
                                        Text("PnL: ${String.format(Locale.US, "%+,.2f", it)} USDT (${String.format(Locale.US, "%+.2f", t.realizedPnlPct ?: 0.0)}%)", color = pnlColor(it), fontSize = MaterialTheme.typography.bodySmall.fontSize)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Ações
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.runCycleNow() }, enabled = !busy, modifier = Modifier.weight(1f)) {
                Text(if (busy) "Analisando…" else "Analisar agora")
            }
            OutlinedButton(onClick = { vm.refreshRunningState() }) { Text("Atualizar") }
        }
        if (busy) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
        manualStatus?.let { Text(it, color = Muted, fontSize = MaterialTheme.typography.bodySmall.fontSize) }

        Text(
            "Simulação com dinheiro fictício e dados reais de mercado. Nenhuma ordem real é enviada. Sem login, sem conta, sem servidor próprio.",
            color = Muted, fontSize = MaterialTheme.typography.labelSmall.fontSize
        )
    }
}
