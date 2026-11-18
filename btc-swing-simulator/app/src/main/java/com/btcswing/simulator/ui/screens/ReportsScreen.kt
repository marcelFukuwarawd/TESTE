package com.btcswing.simulator.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.btcswing.simulator.MainViewModel
import com.btcswing.simulator.ui.components.Muted
import com.btcswing.simulator.ui.components.fmtTime
import com.btcswing.simulator.ui.components.pnlColor
import com.btcswing.simulator.ui.components.usdt
import java.util.Locale

@Composable
fun ReportsScreen(vm: MainViewModel) {
    val state by vm.uiState.collectAsState()
    val reports = state.reports

    if (reports.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text("Nenhum relatório ainda", fontWeight = FontWeight.Bold)
            Text(
                "Relatórios diários são gerados automaticamente às ${String.format(Locale.getDefault(), "%02d", state.settings.dailyReportHour)}:00 (horário configurável em Ajustes) com todas as operações, PnL em USDT e %, saldo final e desempenho acumulado.",
                color = Muted
            )
        }
        return
    }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text(
                "Relatórios diários (toque para expandir)",
                fontWeight = FontWeight.SemiBold, color = Muted
            )
        }
        items(reports, key = { it.id }) { r ->
            var expanded by remember(r.id) { mutableStateOf(false) }
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(fmtTime(r.generatedAt), fontWeight = FontWeight.Bold)
                    Text(
                        "PnL realizado: ${String.format(Locale.US, "%+,.2f", r.realizedPnlUsdt)} USDT (${String.format(Locale.US, "%+.2f%%", r.realizedPnlPct)})",
                        color = pnlColor(r.realizedPnlUsdt)
                    )
                    Text(
                        "${r.tradeCount} operações • ${r.winCount} ganhos / ${r.lossCount} perdas • saldo final ${usdt(r.finalBalanceUsdt)} USDT",
                        color = Muted, fontSize = MaterialTheme.typography.bodySmall.fontSize
                    )
                    Text(
                        "Acumulado: ${String.format(Locale.US, "%+,.2f", r.cumulativePnlUsdt)} USDT (${String.format(Locale.US, "%+.2f%%", r.cumulativePnlPct)}) • drawdown máx ${String.format(Locale.US, "%.2f", r.maxDrawdownPct)}%",
                        color = Muted, fontSize = MaterialTheme.typography.bodySmall.fontSize
                    )
                    if (expanded) {
                        Text("")
                        r.summaryLines.split("\n").forEach {
                            Text(it, fontSize = MaterialTheme.typography.bodySmall.fontSize)
                        }
                    }
                }
            }
        }
    }
}
