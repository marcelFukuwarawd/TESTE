# Fontes de dados

O app usa **apenas APIs públicas e gratuitas de cotações** — sem chave de API,
sem cadastro, sem autenticação, sem custos. Nenhum dado pessoal sai do aparelho.

## 1. Primária — Binance Spot (API pública de mercado)

- **Endpoint**: `GET https://api.binance.com/api/v3/klines?symbol=BTCUSDT&interval=<tf>&limit=300`
- **Docs**: https://developers.binance.com/docs/binance-spot-api-docs/rest-api/market-data-endpoints
- **Autenticação**: nenhuma (endpoint público de market data).
- **Conteúdo**: candles OHLCV reais `[openTime, open, high, low, close, volume, closeTime, ...]`.
- **Intervalos usados**: `5m`, `15m`, `1h`, `4h`, `1d` (nativos).
- **Limites**: peso de IP generoso para uso pessoal (o app faz ~6 requisições por ciclo,
  a cada ≥5 min — muito abaixo de qualquer limite público).

### Hosts espelho (resiliência regional)
A ordem de tentativa é:
1. `https://api.binance.com`
2. `https://data-api.binance.vision` — espelho público oficial de market data
3. `https://api1.binance.com`
4. `https://api2.binance.com`
5. `https://api3.binance.com`

Se todos falharem (ex.: bloqueio geográfico de rede), o app tenta a fonte reserva.

## 2. Reserva — OKX (API pública de mercado)

- **Endpoint**: `GET https://www.okx.com/api/v5/market/candles?instId=BTC-USDT&bar=<bar>&limit=300`
- **Docs**: https://www.okx.com/docs-v5/en/#order-book-trading-market-data-get-candlesticks
- **Autenticação**: nenhuma (endpoint público).
- **Barras usadas**: `5m`, `15m`, `1H`, `4H`, `1D`.
- **Diferença de formato**: resposta `{"code":"0","data":[[ts,o,h,l,c,vol,...],...]}`,
  mais recente primeiro — o app reordena cronologicamente.

## 3. Último recurso — cache local (Room)

Cada busca bem-sucedida grava os candles no banco local (`candles`).
Sem rede em todas as fontes, o ciclo usa o cache — **dados reais baixados antes**,
explicitamente marcados como `stale` no log, na notificação e no widget
("⚠ dados em cache"). Preços muito antigos não geram operações confiáveis;
por isso o app sinaliza em vez de esconder.

Se não houver nem cache suficiente (<30 candles por TF), a decisão é
**WAIT — "Dados insuficientes… Nenhuma operação inventada."**

## 4. Timeframe de 10 minutos (sintetizado)

Nenhuma exchange pública oferece barra nativa de 10m. O app **sintetiza o 10m
localmente** a partir dos candles reais de 5m:

- Agrupamento por bucket de 600 000 ms alinhado à epoch Unix;
- `open` = open do primeiro 5m do bucket; `close` = close do último;
- `high` = máximo dos highs; `low` = mínimo dos lows; `volume` = soma.

É exatamente o que plataformas gráficas fazem ao agregar timeframes — determinístico,
sem interpolação e sem invenção de preços. Implementação: `CandleAggregator`
(com testes unitários dedicados).

## O que NÃO é usado
- Nenhuma API de trading/ordens (o app nunca envia ordens);
- Nenhuma API com chave/conta (Binance Auth, OKX private etc.);
- Nenhum servidor próprio, backend ou serviço de terceiros além dos endpoints públicos acima;
- Nenhuma biblioteca de rastreamento/analytics.
