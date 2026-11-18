# Limitações conhecidas (leia antes de usar)

## Da simulação
1. **Long-only spot**: o robô só compra e vende à vista. Não há short, alavancagem,
   margem, futuros ou stop móvel (trailing).
2. **Execução no preço do último candle fechado**: o slippage simulado (padrão 5 bps)
   approxima o impacto, mas execuções reais em alta volatilidade podem divergir muito.
3. **Sem depth de livro de ofertas**: liquidez é considerada infinita; posições grandes
   (capital fictício alto + positionSizePct alto) não sofreriam impacto realista.
4. **Ciclos discretos**: SL/TP são conferidos a cada ciclo (≥5 min), não tick a tick.
   Um candle pode ultrapassar o stop entre ciclos e a venda simulada ocorre no preço
   do ciclo seguinte — tal como um robô real baseado em polling.
5. **Timeframe 10m sintetizado**: agregação local de candles 5m (correto e determinístico,
   porém não é uma barra "oficial" de exchange).
6. **SMA200 limitada**: com 300 candles por busca, médias de 200 períodos existem em
   todos os TFs exceto quando o histórico público é curto; o snapshot usa SMA50 como
   referência de longo prazo nesses casos (documentado no código).

## Dos dados
7. **Dependência de APIs públicas**: Binance/OKX podem mudar endpoints, limites ou
   disponibilidade regional sem aviso. O app mitiga com 5 hosts + fonte reserva + cache,
   mas uma mudança estrutural exigiria atualização do app.
8. **Preço spot ≠ preço de outras exchanges**: pequenas diferenças de preço entre
   exchanges são normais; a simulação usa a fonte que respondeu.
9. **Sem websocket**: o app faz polling REST a cada ciclo (mais simples, mais leve e
   suficiente para swing trade; não serve para scalping).

## Do Android/Xiaomi
10. **MIUI/HyperOS agressivo**: sem habilitar Auto-start + "Sem restrições" de bateria
    (telas oficiais, com atalho no app), o sistema pode matar o serviço. O watchdog do
    WorkManager religa em ~15 min, mas pode haver lacunas.
11. **Android 15+**: serviços foreground `dataSync` têm limite oficial de 6h/24h,
    **exceto** para apps isentos de otimização de bateria — por isso o app oferece o
    diálogo oficial de isenção. Sem a isenção, o watchdog religa o serviço após o corte.
12. **Doze profundo**: com o aparelho parado por horas (tela off, sem carregar), o
    Android adia trabalhos de rede. O relatório diário pode sair minutos/horas depois
    do horário exato nesses cenários (o WorkManager executa na primeira janela viável).
13. **Reboot**: o app religa sozinho via BOOT_COMPLETED, mas alguns launches MIUI exigem
    Auto-start habilitado para que o receiver rode antes do primeiro desbloqueio.

## Do projeto
14. **Assinatura do APK**: sem configurar keystore próprio nos secrets do GitHub, o
    release é assinado com chave debug do CI. Instalável, mas trocas de assinatura
    exigem desinstalar a versão anterior.
15. **Testes instrumentados fora do CI**: o GitHub Actions roda os 41 testes unitários
    (núcleo de decisão/simulação). Testes em emulador exigem rodar localmente ou um
    self-hosted runner com AVD — não fazem parte do fluxo padrão.
16. **Nenhum backtest automatizado**: o app simula para frente (forward simulation).
    Avaliar a estratégia historicamente exigiria um módulo de backtest (não incluído).

## Riscos e isenção
- Isto é material **educacional**. Resultados simulados não representam nem garantem
  resultados reais de trading.
- Criptomoedas são ativos de altíssima volatilidade; a maioria dos traders de varejo
  perde dinheiro. Nunca arrisque o que não pode perder.
- O app não é, e não pretende ser, aconselhamento financeiro.
