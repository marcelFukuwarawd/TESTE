package com.btcswing.simulator.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.btcswing.simulator.MainViewModel
import com.btcswing.simulator.ui.components.Green
import com.btcswing.simulator.ui.components.Muted
import com.btcswing.simulator.ui.components.Red
import com.btcswing.simulator.ui.components.fmtTime
import com.btcswing.simulator.ui.components.usdt
import org.json.JSONObject
import java.util.Locale

@Composable
fun AnalysisScreen(vm: MainViewModel) {
    val state by vm.uiState.collectAsState()
    val busy by vm.busy.collectAsState()
    val decisions = state.decisions

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "Análise multi-timeframe: 5m, 10m*, 15m, 1h, 4h e 1d com RSI, MACD, médias móveis (SMA/EMA 20/50/200), ADX, Bollinger, volume, OBV e ATR.",
            color = Muted, fontSize = MaterialTheme.typography.bodySmall.fontSize
        )
        Text(
            "* O timeframe de 10m é sintetizado localmente a partir de candles reais de 5m (corretoras não fornecem barra nativa de 10m).",
            color = Muted, fontSize = MaterialTheme.typography.labelSmall.fontSize
        )

        Button(onClick = { vm.runCycleNow() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(if (busy) "Analisando…" else "Rodar análise agora")
        }

        if (decisions.isEmpty()) {
            Text("Nenhuma análise registrada ainda.", color = Muted)
            return@Column
        }

        decisions.take(20).forEach { d ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(14.dp)) {
                    val action = when (d.action) { "BUY" -> "COMPRA"; "SELL" -> "VENDA"; else -> "ESPERA" }
                    val color = when (d.action) { "BUY" -> Green; "SELL" -> Red; else -> Color.White }
                    Text("$action • score ${String.format(Locale.US, "%+.1f", d.score)}", color = color, fontWeight = FontWeight.Bold)
                    Text(
                        "${fmtTime(d.time)} • preço $${usdt(d.price)}" + if (d.executed) " • EXECUTADA" else "",
                        color = Muted, fontSize = MaterialTheme.typography.bodySmall.fontSize
                    )
                    // Scores por timeframe (JSON persistido)
                    runCatching {
                        val obj = JSONObject(d.scoresByTf)
                        val parts = obj.keys().asSequence().map { k ->
                            "$k: ${String.format(Locale.US, "%+.0f", obj.optDouble(k))}"
                        }.toList()
                        if (parts.isNotEmpty()) Text(
                            "Scores por TF → " + parts.joinToString(" | "),
                            fontSize = MaterialTheme.typography.bodySmall.fontSize,
                            color = Muted
                        )
                    }
                    d.reasons.split("\n").forEach { line ->
                        Text(line, fontSize = MaterialTheme.typography.bodySmall.fontSize)
                    }
                }
            }
        }
    }
}
