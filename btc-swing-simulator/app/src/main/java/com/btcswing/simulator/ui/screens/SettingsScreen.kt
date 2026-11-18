package com.btcswing.simulator.ui.screens

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.btcswing.simulator.MainViewModel
import com.btcswing.simulator.domain.model.BotSettings
import com.btcswing.simulator.ui.components.Muted

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current
    val s = state.settings

    // Campos editáveis (texto livre → parse numérico ao salvar)
    var capital by remember(s) { mutableStateOf(s.initialCapitalUsdt.toString()) }
    var posSize by remember(s) { mutableStateOf(s.positionSizePct.toString()) }
    var fee by remember(s) { mutableStateOf(s.feePct.toString()) }
    var slippage by remember(s) { mutableStateOf(s.slippageBps.toString()) }
    var stopPct by remember(s) { mutableStateOf(s.stopLossPct.toString()) }
    var tpPct by remember(s) { mutableStateOf(s.takeProfitPct.toString()) }
    var stopAtr by remember(s) { mutableStateOf(s.stopAtrMult.toString()) }
    var tpAtr by remember(s) { mutableStateOf(s.tpAtrMult.toString()) }
    var buyTh by remember(s) { mutableStateOf(s.buyThreshold.toString()) }
    var sellTh by remember(s) { mutableStateOf(s.sellThreshold.toString()) }
    var interval by remember(s) { mutableStateOf(s.analysisIntervalMinutes.toString()) }
    var cooldown by remember(s) { mutableStateOf(s.cooldownMinutes.toString()) }
    var reportHour by remember(s) { mutableStateOf(s.dailyReportHour.toString()) }
    var useAtr by remember(s) { mutableStateOf(s.useAtrStops) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Section("Capital e risco (dinheiro fictício)")
        NumField("Capital inicial (USDT fictícios)", capital) { capital = it }
        NumField("% do patrimônio por compra (1–100)", posSize) { posSize = it }
        NumField("Taxa por ordem (% — spot real ≈ 0,10)", fee) { fee = it }
        NumField("Slippage simulado (bps, sempre contra você)", slippage) { slippage = it }

        Section("Stop-loss / Take-profit")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Usar ATR (1h) para SL/TP", Modifier.weight(1f))
            Switch(checked = useAtr, onCheckedChange = { useAtr = it })
        }
        if (useAtr) {
            NumField("Multiplicador ATR do stop-loss", stopAtr) { stopAtr = it }
            NumField("Multiplicador ATR do take-profit", tpAtr) { tpAtr = it }
        } else {
            NumField("Stop-loss (%)", stopPct) { stopPct = it }
            NumField("Take-profit (%)", tpPct) { tpPct = it }
        }

        Section("Estratégia e cadência")
        NumField("Score mínimo para comprar (0–100)", buyTh) { buyTh = it }
        NumField("Score mínimo para vender (0–100)", sellTh) { sellTh = it }
        NumField("Intervalo entre análises (min, mín. 5)", interval) { interval = it }
        NumField("Cooldown entre operações (min)", cooldown) { cooldown = it }

        Section("Relatório diário")
        NumField("Hora do relatório (0–23, fuso do aparelho)", reportHour) { reportHour = it }

        Button(onClick = {
            vm.saveSettings(
                BotSettings(
                    initialCapitalUsdt = capital.d(),
                    positionSizePct = posSize.d(),
                    feePct = fee.d(),
                    slippageBps = slippage.d(),
                    minNotionalUsdt = s.minNotionalUsdt,
                    stopLossPct = stopPct.d(),
                    takeProfitPct = tpPct.d(),
                    useAtrStops = useAtr,
                    stopAtrMult = stopAtr.d(),
                    tpAtrMult = tpAtr.d(),
                    buyThreshold = buyTh.d(),
                    sellThreshold = sellTh.d(),
                    analysisIntervalMinutes = interval.i(),
                    cooldownMinutes = cooldown.i(),
                    dailyReportHour = reportHour.i(),
                    botEnabled = s.botEnabled,
                    symbol = s.symbol
                )
            )
        }, modifier = Modifier.fillMaxWidth()) { Text("Salvar configurações") }

        Section("Funcionamento contínuo (mecanismos oficiais)")
        OutlinedButton(onClick = { requestIgnoreBatteryOptimizations(context) }, modifier = Modifier.fillMaxWidth()) {
            Text("Liberar otimização de bateria (dialog oficial do Android)")
        }
        OutlinedButton(onClick = { openMiuiAutostart(context) }, modifier = Modifier.fillMaxWidth()) {
            Text("Abrir Auto-start da MIUI/HyperOS (Xiaomi)")
        }
        Text(
            "O app usa apenas mecanismos oficiais: serviço em primeiro plano (dataSync) com notificação persistente, WorkManager para o relatório diário e watchdog, e reinício automático após boot. Nada aqui burla a segurança do sistema. No Xiaomi 12T Pro (MIUI/HyperOS), habilite também 'Início automático' e defina a bateria do app como 'Sem restrições' para máxima confiabilidade.",
            color = Muted, fontSize = MaterialTheme.typography.bodySmall.fontSize
        )

        Section("Zona de perigo")
        OutlinedButton(
            onClick = { vm.resetSimulation(capital.d()) },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Reiniciar simulação (apaga TODO o histórico)") }

        Section("Dados e privacidade")
        Text(
            "Fontes: API pública spot da Binance (com hosts espelho, incluindo data-api.binance.vision) e, como reserva, API pública da OKX. Apenas dados de mercado — sem chave de API, sem login, sem cadastro, sem coleta de dados pessoais, sem servidor próprio. Tudo (histórico, decisões, relatórios) fica somente no aparelho.",
            color = Muted, fontSize = MaterialTheme.typography.bodySmall.fontSize
        )
    }
}

@Composable
private fun Section(title: String) {
    Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun NumField(label: String, value: String, onValue: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label, fontSize = MaterialTheme.typography.bodySmall.fontSize) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

private fun String.d(): Double = toDoubleOrNull() ?: Double.NaN
private fun String.i(): Int = toIntOrNull() ?: -1

/** Diálogo OFICIAL do Android para isentar o app da otimização de bateria. */
private fun requestIgnoreBatteryOptimizations(context: Context) {
    try {
        val pm = context.getSystemService(PowerManager::class.java)
        if (pm?.isIgnoringBatteryOptimizations(context.packageName) == true) {
            Toast.makeText(context, "Otimização de bateria já está liberada para este app.", Toast.LENGTH_LONG).show()
            return
        }
        val intent = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}")
        )
        context.startActivity(intent)
    } catch (t: Throwable) {
        Toast.makeText(context, "Não foi possível abrir o diálogo oficial: ${t.message}", Toast.LENGTH_LONG).show()
    }
}

/** Atalho para a tela oficial de Auto-start da MIUI/HyperOS (Xiaomi). */
private fun openMiuiAutostart(context: Context) {
    val candidates = listOf(
        ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
        ComponentName("com.miui.securitycenter", "com.miui.powercenter.PowerSettings")
    )
    for (cn in candidates) {
        try {
            context.startActivity(Intent().setComponent(cn))
            return
        } catch (_: Throwable) { }
    }
    Toast.makeText(
        context,
        "Abra manualmente: Configurações MIUI → Apps → Gerenciar apps → BTC Swing → Início automático",
        Toast.LENGTH_LONG
    ).show()
}
