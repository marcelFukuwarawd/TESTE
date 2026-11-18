# ₿ BTC Swing Simulator

**Simulador autônomo de swing trade BTC/USDT para Android** — dinheiro 100% fictício, dados 100% reais de mercado.

O robô analisa gráficos de **5m, 10m*, 15m, 1h, 4h e 1d** com **RSI, MACD, médias móveis (SMA/EMA 20/50/200), ADX, Bollinger, volume, OBV e ATR**, decide automaticamente **COMPRA / VENDA / ESPERA**, registra tudo, simula **taxas e slippage**, roda **em segundo plano** com notificação persistente, sobrevive à **reinicialização do aparelho**, gera **relatório diário** no horário que você escolher e exibe um **widget grande e redimensionável** na tela inicial.

> ⚠️ **Aviso importante**: este app é uma **simulação educacional**. Nenhum dinheiro real é usado, nenhuma ordem real é enviada a nenhuma corretora. Resultados de simulação **não garantem** resultados reais. Trading de criptomoedas envolve alto risco.

---

## ✅ O que este projeto garante

| Requisito | Como é atendido |
|---|---|
| Projeto Android completo em Kotlin | Jetpack Compose + Room + DataStore + WorkManager + Foreground Service |
| APK gerado **somente pelo GitHub** | GitHub Actions com JDK 17 + Android SDK na nuvem — nada para instalar no seu PC ([docs/BUILD_GITHUB.md](docs/BUILD_GITHUB.md)) |
| Compatível com **Xiaomi 12T Pro** | minSdk 26 / targetSdk 34, ABI `arm64-v8a` (Dimensity 9000+), instruções MIUI/HyperOS ([docs/XIAOMI_12T_PRO.md](docs/XIAOMI_12T_PRO.md)) |
| **Sem login/cadastro/conta** em lugar nenhum | Nenhuma tela, API ou permissão de autenticação existe no app |
| Tudo local, sem servidor externo | Banco Room + DataStore no aparelho; internet só para APIs públicas de candles |
| Sem modos próprios de economia de bateria | Somente mecanismos oficiais: foreground service `dataSync`, wake lock curto por ciclo, WorkManager, diálogo oficial de isenção de bateria |
| Capital inicial definido pelo usuário | Tela Ajustes → "Capital inicial (USDT fictícios)" |
| Decisões automáticas registradas | Log completo de todas as decisões (inclusive ESPERA) com score e justificativas |
| Taxas e slippage simulados | Configuráveis (padrão: 0,10% de taxa + 5 bps de slippage adverso) |
| Segundo plano + notificação persistente | `TradingForegroundService` (tipo `dataSync`) |
| Recuperação após reinicialização | `BootReceiver` + watchdog `EnsureServiceWorker` (WorkManager) |
| Sem inventar operações | Sem candles reais → decisão WAIT registrada; nunca há execução sem dados ([docs/SIMULACAO.md](docs/SIMULACAO.md)) |
| Relatório a cada 24h no horário escolhido | `DailyReportWorker` + notificação + tela Relatórios |
| Widget 6x4/6x6 redimensionável | `BtcWidgetProvider` com RemoteViews — mais detalhes conforme o espaço |
| Fontes de dados documentadas | [docs/FONTES_DE_DADOS.md](docs/FONTES_DE_DADOS.md) |
| Limitações documentadas | [docs/LIMITACOES.md](docs/LIMITACOES.md) |
| Testes básicos | 41 testes unitários (indicadores, estratégia, simulação, relatório, agregação) + testes instrumentados |

\* O timeframe de 10m **não existe nativamente** nas exchanges — ele é sintetizado localmente agrupando candles reais de 5m (2×5m), de forma determinística e documentada.

---

## 🚀 Como obter o APK (sem instalar NADA no computador)

Resumo (passo a passo completo com imagens mentais em [docs/BUILD_GITHUB.md](docs/BUILD_GITHUB.md)):

1. Crie uma conta gratuita em [github.com](https://github.com) (a única "conta" envolvida é a do GitHub — o app em si não tem conta nenhuma).
2. Crie um novo repositório (pode ser privado) e **faça upload desta pasta** pela interface web do GitHub: *Add file → Upload files* (arraste todas as pastas/arquivos, incluindo `.github`).
3. Vá na aba **Actions** do repositório. Se aparecer um aviso pedindo para habilitar workflows, clique em *I understand my workflows, go ahead and enable them*.
4. O push dispara automaticamente o workflow **"Build APK"**: roda testes unitários → compila debug + release → publica os APKs como **artifact**.
5. Ainda na aba Actions, abra a execução concluída e baixe `apk-xiaomi-12t-pro` em *Artifacts*. Extraia o zip e envie o `.apk` para o celular (cabo, Drive, Telegram etc.).
6. No Xiaomi 12T Pro: permita "Instalar apps desconhecidos" para o navegador/gerenciador de arquivos e instale.

Também é possível disparar manualmente: **Actions → Build APK → Run workflow** (escolha `debug`, `release` ou `both`).
Criando uma tag `v1.0.0`, o workflow publica automaticamente uma **Release** com os APKs anexados.

**Assinatura**: sem configurar secrets, o release é assinado com a chave debug do próprio CI — perfeitamente instalável. Para assinatura própria (recomendado para reinstalações futuras consistentes), crie os secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` no repositório (instruções em [docs/BUILD_GITHUB.md](docs/BUILD_GITHUB.md)).

---

## 📱 Configuração recomendada no Xiaomi 12T Pro (MIUI/HyperOS)

Para funcionamento contínuo confiável (tudo por caminhos **oficiais** do sistema):

1. Abra o app → aba **Ajustes** → **"Liberar otimização de bateria"** (abre o diálogo oficial do Android) → permitir.
2. Ainda em Ajustes → **"Abrir Auto-start da MIUI/HyperOS"** → habilite o início automático do app.
3. Configurações do sistema → Bateria → apps → BTC Swing Simulator → **Sem restrições**.
4. Adicione o **widget** (pressione a tela inicial → Widgets → "BTC Swing — Robô") e redimensione como quiser (6x4, 6x6…).
5. Defina o **capital inicial fictício**, o **horário do relatório diário** e ligue o robô no toggle da tela Início.

Detalhes e solução de problemas: [docs/XIAOMI_12T_PRO.md](docs/XIAOMI_12T_PRO.md).

---

## 🧠 Como o robô decide (resumo)

Cada ciclo (padrão: a cada 15 min, configurável 5–240):

1. Busca candles **reais** de 6 timeframes (Binance pública → OKX pública → cache local marcado como obsoleto; se nada existir → **WAIT**, nada é inventado).
2. Calcula RSI(14), MACD(12/26/9), SMA/EMA 20/50/200, ADX(14), Bollinger(20, 2σ), volume vs. média, OBV e ATR(14) em cada TF.
3. Cada TF recebe um score em [-1, +1] (tendência 35%, MACD 25%, RSI 20%, Bollinger 10%, confirmação de volume/OBV 10%, modulado pelo ADX). Os scores são combinados com pesos: **D1 25% + H4 20% + H1 20% + M15 15% + M10 10% + M5 10%**.
4. Decisão:
   - **COMPRA** se score ≥ limiar (padrão 55), sem posição aberta e fora de cooldown;
   - **VENDA** se score ≤ −limiar com posição aberta, **ou** se stop-loss/take-profit forem atingidos (têm prioridade);
   - **ESPERA** nos demais casos — toda espera também é registrada com justificativa.
5. Execução simulada: slippage adverso (compra paga mais caro, venda recebe menos) + taxa sobre o notional; SL/TP por ATR(1h) ou percentual.
6. Persistência em banco local → atualização de widget → notificações de operações.

Long-only spot (sem alavancagem, sem short). Fórmula completa, exemplos numéricos e o contrato anti-invenção em [docs/SIMULACAO.md](docs/SIMULACAO.md).

---

## 🗂 Estrutura do projeto

```
btc-swing-simulator/
├── .github/workflows/build-apk.yml      # CI: testes + APK debug/release + Release em tags
├── app/src/main/java/com/btcswing/simulator/
│   ├── BtcSwingApp.kt                   # Application: canais de notificação + DI init
│   ├── MainActivity.kt                  # Compose, navegação 5 abas, permissão de notificação
│   ├── MainViewModel.kt                 # Estado agregado (Room + DataStore em Flow)
│   ├── di/ServiceLocator.kt             # DI manual (sem Hilt — menos risco no CI)
│   ├── domain/
│   │   ├── indicators/                  # RSI, MACD, ADX, Bollinger, OBV, ATR, SMAs/EMAs (puro)
│   │   ├── model/BotSettings.kt         # Configurações do usuário (puro, sanitizado)
│   │   ├── simulation/                  # Motor de execução fictícia + relatório diário (puro)
│   │   └── strategy/                    # Timeframes + decisão multi-TF (puro)
│   ├── data/
│   │   ├── api/                         # Binance (5 hosts) + OKX reserva + repositório/cache
│   │   ├── db/                          # Room: trades, decisões, candles, portfolio, equity, relatórios
│   │   └── prefs/                       # DataStore (capital, risco, horários…)
│   ├── service/                         # Foreground service, engine, boot, workers, notificações
│   ├── widget/                          # Widget redimensionável + estado
│   └── ui/                              # 5 telas Compose (Início, Operações, Análise, Relatórios, Ajustes)
├── app/src/test/                        # 41 testes unitários JVM (núcleo validado localmente)
├── app/src/androidTest/                 # Testes instrumentados básicos
└── docs/                                # Fontes de dados, simulação, limitações, build, Xiaomi
```

## 🧪 Testes

- **Unitários (JVM, rodam no CI a cada build)**: `./gradlew testDebugUnitTest` — indicadores (RSI/MACD/ADX/Bollinger/ATR/OBV/SMA/EMA), estratégia (BUY/SELL/WAIT, cooldown, long-only, pesos), simulação (slippage, taxas, PnL, SL/TP, rejeições, anti-invenção), relatório diário (PnL, drawdown, casos vazios) e agregação 5m→10m.
- **Instrumentados (emulador/aparelho, fora do CI)**: contexto, banco Room e recursos.

## 🔒 Privacidade

Sem coleta de dados, sem analytics, sem crash reporting, sem contas. O app conversa apenas com endpoints **públicos de cotações** (Binance/OKX) e guarda todo o histórico **somente no aparelho**. Não há servidor próprio nem de terceiros envolvido.

## 📄 Documentação

- [docs/BUILD_GITHUB.md](docs/BUILD_GITHUB.md) — gerar o APK só com o GitHub (passo a passo)
- [docs/XIAOMI_12T_PRO.md](docs/XIAOMI_12T_PRO.md) — instalação e confiabilidade no 12T Pro
- [docs/FONTES_DE_DADOS.md](docs/FONTES_DE_DADOS.md) — APIs, endpoints, fallbacks, síntese do 10m
- [docs/SIMULACAO.md](docs/SIMULACAO.md) — matemática da simulação (taxas, slippage, PnL, relatório)
- [docs/LIMITACOES.md](docs/LIMITACOES.md) — o que o app **não** faz e riscos conhecidos

## Licença

MIT — use, estude e modifique à vontade.
