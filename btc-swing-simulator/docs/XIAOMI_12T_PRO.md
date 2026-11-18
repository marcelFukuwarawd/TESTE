# Xiaomi 12T Pro — instalação e funcionamento contínuo

O Xiaomi 12T Pro (Dimensity 9000+, MIUI/HyperOS sobre Android 12+) é totalmente
compatível: o APK é `arm64-v8a`, minSdk 26, targetSdk 34.

## Instalação do APK
1. Envie o `.apk` gerado pelo GitHub Actions para o celular.
2. Toque no arquivo (gerenciador de arquivos ou notificação de download).
3. O MIUI pedirá permissão: **Configurações → permitir desta fonte**
   (Instalar apps desconhecidos) para o navegador/gerenciador usado.
4. Se aparecer o aviso "Verificar com o Play Protect", escolha **Instalar assim mesmo**.
5. Pronto — o app não pede login nem cadastro em momento algum.

## Ajustes recomendados (todos por caminhos oficiais)
A MIUI/HyperOS é agressiva com apps em segundo plano. O app usa apenas mecanismos
oficiais do Android (foreground service, WorkManager, boot receiver) e oferece
atalhos para as telas oficiais do sistema:

1. **Isenção de otimização de bateria** — no app: *Ajustes → Liberar otimização de
   bateria* (abre o diálogo oficial `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`).
   Benefício extra: no Android 15+, apps isentos não sofrem o limite de 6h/dia
   imposto a serviços `dataSync`.
2. **Início automático (Auto-start)** — no app: *Ajustes → Abrir Auto-start da
   MIUI/HyperOS*. Habilite a chave do app. Isso garante religar após reboot mesmo
   antes do primeiro desbloqueio de tela.
3. **Bateria sem restrições** — Configurações do sistema → Bateria → (engrenagem)
   → apps → BTC Swing Simulator → **Sem restrições**.
4. **Notificações** — aceite o pedido de permissão ao abrir o app pela primeira vez
   (Android 13+). A notificação persistente do robô é de baixa importância (não toca som).
5. **Widget** — pressione a tela inicial → Widgets → **BTC Swing — Robô**.
   Arraste para redimensionar: em widgets altos (≈6x6) aparecem as últimas operações;
   em menores, o essencial (saldo, preço, P/L, última decisão).

## Comportamento após reinicialização
- `BOOT_COMPLETED` / `QUICKBOOT_POWERON` → o `BootReceiver` religa o serviço
  (se o robô estava ligado) e reagenda os workers.
- Se o sistema matar o processo em qualquer momento, o watchdog `EnsureServiceWorker`
  (WorkManager, a cada ~15 min) detecta e religa o serviço.
- O estado (caixa, posição, stop/target, histórico) fica no banco local: a retomada
  continua exatamente de onde parou, **sem criar operações retroativas** do período
  em que o app ficou morto. O próximo ciclo simplesmente analisa os candles reais
  do momento.

## Solução de problemas
| Sintoma | Causa provável | Correção |
|---|---|---|
| Robô para de analisar após minutos | MIUI matou o serviço | Faça os passos 1–3 acima |
| Sem notificação persistente | Permissão de notificação negada | Configurações → Apps → BTC Swing → Notificações → permitir |
| Widget mostra "--" | Nenhum ciclo executado ainda | Abra o app → "Analisar agora" |
| "dados em cache" na notificação | Sem internet no momento | Normal — o app usa os últimos candles reais baixados e sinaliza; nada é inventado |
| Relatório diário atrasado | Aparelho em Doze profundo no horário | Com isenção de bateria (passo 1), o WorkManager dispara pontualmente |
