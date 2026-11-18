# Como gerar o APK usando APENAS o GitHub (sem instalar nada no PC)

O workflow `.github/workflows/build-apk.yml` provisiona na nuvem do GitHub:
JDK 17 (Temurin), Android SDK e Gradle 8.7 (via wrapper incluído no repositório).
Seu computador precisa apenas de um **navegador**.

## Passo a passo

### 1. Criar a conta e o repositório
1. Acesse [github.com](https://github.com) e crie uma conta gratuita (se não tiver).
2. Clique em **+ → New repository**.
3. Nome sugerido: `btc-swing-simulator`. Pode ser **Private**. Não marque "Add README" (você vai enviar o nosso).

### 2. Enviar os arquivos pela web (sem git instalado)
1. Na página do repositório vazio, clique em **uploading an existing file**
   (ou **Add file → Upload files**).
2. Arraste **todo o conteúdo desta pasta**, incluindo:
   - `.github/` (a pasta do workflow — arquivos iniciados com ponto são aceitos pelo upload web)
   - `app/`, `gradle/` (incluindo `gradle/wrapper/gradle-wrapper.jar`, binário de ~49 KB)
   - `gradlew`, `gradlew.bat`, `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`
   - `README.md`, `docs/`, `.gitignore`, `.gitattributes`
3. Clique em **Commit changes**.

> Dica: o upload web aceita arrastar pastas inteiras do gerenciador de arquivos.
> Se algum arquivo for recusado, envie em lotes menores — a ordem não importa.

### 3. Habilitar e acompanhar o workflow
1. Vá na aba **Actions** do repositório.
2. Se aparecer o aviso *"Workflows aren't being run on this fork/repository"*,
   clique em **I understand my workflows, go ahead and enable them**.
3. O push inicial já dispara o workflow **Build APK** automaticamente.
   Clique nele para ver o progresso (≈8–15 min na primeira vez; depois, mais rápido com cache).

O que o workflow faz, em ordem:
1. Checkout do código;
2. Instala JDK 17 + Android SDK no runner;
3. Restaura cache do Gradle;
4. **Roda os 41 testes unitários** — se algum falhar, o build para (qualidade garantida);
5. `assembleDebug` e `assembleRelease`;
6. Renomeia os APKs para `BtcSwingSimulator-<tipo>-<sha>-arm64.apk`;
7. Publica o artifact **`apk-xiaomi-12t-pro`**;
8. Em tags `v*`, cria uma **Release** com os APKs anexados.

### 4. Baixar o APK
1. Na execução concluída (✓ verde), role até **Artifacts**.
2. Baixe **`apk-xiaomi-12t-pro`** (zip).
3. Extraia e envie o `.apk` para o celular (Google Drive, cabo USB, Telegram etc.).

> Artifacts ficam disponíveis por 90 dias e exigem login na sua conta GitHub
> para download (normal — o APK em si não tem conta nenhuma).

### 5. Builds seguintes
- Qualquer **push** em `main`/`master` gera novos APKs automaticamente.
- Para disparar manualmente: **Actions → Build APK → Run workflow** e escolha
  `debug`, `release` ou `both`.
- Para publicar uma versão: crie uma tag (`Releases → Draft a new release → tag v1.0.0`)
  e o workflow anexa os APKs na Release automaticamente.

## Qual APK instalar?
- **`...-debug-...apk`**: id `com.btcswing.simulator.debug`. Bom para testar.
- **`...-release-...apk`**: id `com.btcswing.simulator`. Sem sufixo debug.
  Sem secrets de keystore, é assinado com a chave debug do CI — **instalável normalmente**.

Escolha um e mantenha-o: trocar entre debug/release ou trocar a assinatura
exige desinstalar a versão anterior (o Android bloqueia atualização com assinaturas diferentes).

## Assinatura própria (opcional, recomendado para uso longo)
Sem keystore próprio, cada runner do GitHub gera/usa uma chave debug do CI.
Para ter sempre a mesma assinatura:

1. Em qualquer máquina com Java (ou usando um gerador online de sua confiança —
   preferencialmente gere localmente), crie um keystore:
   `keytool -genkeypair -v -keystore release.keystore -alias btcswing -keyalg RSA -keysize 2048 -validity 10000`
2. Converta para base64: `base64 -w0 release.keystore > keystore.txt`
3. No repositório: **Settings → Secrets and variables → Actions → New repository secret**:
   - `KEYSTORE_BASE64` = conteúdo do keystore.txt
   - `KEYSTORE_PASSWORD` = senha do keystore
   - `KEY_ALIAS` = `btcswing` (o alias escolhido)
   - `KEY_PASSWORD` = senha da chave
4. Rode o workflow novamente — o release passa a usar sua assinatura.

**Guarde o keystore e as senhas**: sem eles, atualizações futuras exigirão desinstalar o app.

## Solução de problemas no CI
| Sintoma | Causa provável | Correção |
|---|---|---|
| `gradle-wrapper.jar not found` | Jar não enviado no upload | Reenvie `gradle/wrapper/gradle-wrapper.jar` (binário) |
| Workflow não roda | Actions desabilitados no repo | Aba Actions → enable |
| Falha em `Setup Android SDK` | Instabilidade do runner | Re-run jobs (botão no topo da execução) |
| Testes falham | Código alterado | Veja o log do step de testes; o repositório original passa 41/41 |
| Download de dependências lento | Primeiro build sem cache | Normal; builds seguintes usam cache do Gradle |
