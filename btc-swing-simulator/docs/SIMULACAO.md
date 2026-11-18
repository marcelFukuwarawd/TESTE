# Como a simulação funciona (matemática e regras)

Tudo roda localmente. Dinheiro fictício; preços reais. Nenhuma ordem é enviada
a corretora alguma — não existe código de trading real no app.

## 1. Ciclo de análise
A cada `analysisIntervalMinutes` (padrão 15, mínimo 5):

1. Busca 300 candles reais por timeframe (5m, 10m*, 15m, 1h, 4h, 1d).
2. Calcula indicadores: RSI(14), MACD(12,26,9), SMA/EMA 20/50/200, ADX(14),
   Bollinger(20, 2σ), volume vs. SMA20 do volume, OBV, ATR(14).
3. Score por timeframe em [-1,+1]:

   | Componente | Peso | Regra |
   |---|---|---|
   | Tendência (preço × SMA20 × SMA50) | 0,35 | +1 alta, −1 baixa, 0 lateral |
   | MACD histograma normalizado | 0,25 | `(hist/preço)×10 000` limitado a ±1 |
   | RSI | 0,20 | ≤30 → +1 (sobrevendido); ≥70 → −1; 45–55 → 0; entre → proporcional |
   | Bollinger | 0,10 | fechamento fora da banda inferior → +1; da superior → −1 |
   | Volume/OBV | 0,10 | confirmação (+1/−1) se momentum e volume > 1,2× a média |
   | ADX | modulador | ≥40 → ×1,15; ≥25 → ×1,0; ≥20 → ×0,85; <20 → ×0,7 |

4. Score agregado = média ponderada por disponibilidade:
   `D1 25% + H4 20% + H1 20% + M15 15% + M10 10% + M5 10%`, em [-100,+100].
5. Decisão:
   - **BUY** se `score ≥ buyThreshold` (55), sem posição e cooldown vencido;
   - **SELL** se `score ≤ −sellThreshold` (−55) com posição aberta;
   - **WAIT** caso contrário (inclusive dados insuficientes/sem rede).
   - Stop-loss e take-profit têm **prioridade** sobre o sinal: se o preço atual
     cruzar SL/TP, a venda é executada com o motivo correspondente.
6. Cooldown: após qualquer operação, novas compras exigem `cooldownMinutes`
   (padrão 60) de intervalo — evita overtrading em ruído de timeframe curto.

\* 10m sintetizado localmente a partir de candles reais de 5m (ver FONTES_DE_DADOS.md).

## 2. Execução fictícia com custos realistas

Compra (preço de mercado `P`, settings `feePct` f e `slippageBps` b):

```
preçoExecutado  = P × (1 + b/10 000)            # slippage CONTRA a compra
orçamento       = caixa × positionSizePct/100
notional        = orçamento / (1 + f/100)        # garante notional+taxa ≤ orçamento
quantidade      = notional / preçoExecutado
taxa            = notional × f/100
caixaNovo       = caixa − (notional + taxa)
costBasis       = notional + taxa                # base p/ PnL da venda futura
```

Venda:

```
preçoExecutado  = P × (1 − b/10 000)            # slippage CONTRA a venda
bruto           = quantidade × preçoExecutado
taxa            = bruto × f/100
líquido         = bruto − taxa
PnL realizado   = líquido − costBasis            # inclui custos dos DOIS lados
PnL %           = PnL / costBasis × 100
```

Rejeições explícitas (nada é executado "na marra"):
- já existe posição aberta;
- sem posição para vender;
- preço inválido/indisponível (NaN, ≤0) — **nunca se opera sem preço real**;
- caixa abaixo do notional mínimo (padrão 10 USDT).

## 3. Stop-loss / Take-profit
- **Modo ATR (padrão)**: `SL = entrada − 1,5×ATR(1h)`, `TP = entrada + 3,0×ATR(1h)`
  (multiplicadores configuráveis) — adapta-se à volatilidade real.
- **Modo percentual**: `SL = entrada × (1 − 2%)`, `TP = entrada × (1 + 4%)`.
- Verificados a cada ciclo contra o último preço real; gatilho gera venda imediata
  com motivo registrado ("stop-loss atingido…" / "take-profit atingido…").
- Long-only spot: sem alavancagem, sem short, sem liquidação.

## 4. Patrimônio (equity)
`equity = caixa + quantidade × preçoAtual` — marcada a mercado a cada ciclo e
gravada em `equity_points` (curva de desempenho, drawdown, relatórios).

## 5. Relatório diário (24 h, horário escolhido)
Gerado pelo `DailyReportWorker` (WorkManager) com dados reais persistidos:
- todas as operações da janela (contagem, ganhos/perdas);
- PnL realizado em USDT e % (sobre o patrimônio no início da janela);
- melhor e pior venda;
- saldo final em caixa e patrimônio total;
- desempenho acumulado desde o capital inicial (USDT e %);
- drawdown máximo da janela;
- posição aberta (se houver) com SL/TP.

O relatório é salvo no banco, exibido na aba Relatórios e enviado como notificação.
Se nenhuma operação ocorreu, o relatório diz explicitamente
"Nenhuma operação no período — o robô permaneceu em espera (WAIT)."

## 6. Contrato anti-invenção (interrupções)
- Decisões usam **somente candles reais** baixados no momento do ciclo (ou cache
  sinalizado como obsoleto);
- Sem dados → WAIT registrado; **nunca** BUY/SELL;
- Se o processo morrer no meio de um ciclo, o banco mantém o último estado
  consistente (a gravação ocorre antes das notificações);
- Após reboot/morte pelo sistema, o próximo ciclo simplesmente analisa o mercado
  **atual** — nenhuma operação retroativa é criada para o período offline;
- Preços inválidos (NaN/≤0) bloqueiam a execução com rejeição registrada.

## 7. Persistência
| Dado | Onde |
|---|---|
| Operações, decisões, candles, patrimônio, relatórios | Room (`btc_swing.db`) |
| Configurações do usuário | DataStore (preferences) |
| Estado do robô (on/off) | DataStore + WorkManager (workers únicos) |
