# Plano — Silenciar os avisos de CDP do Selenium (issue #62)

Spec: `spec.md` nesta pasta. Branch: `issue-62-avisos-cdp`, a partir do `main` em `942b16c`.

## Passos

- [x] **1. Loggers de CDP em `SEVERE` (red → green).**
  - Teste: `SeleniumBrowserTest#avisosDeCdpSilenciados`.
  - Red: o nível é `null`, porque é herdado do pai; falha na asserção.
  - Green: campos `static final Logger` com `setLevel(Level.SEVERE)` no `SeleniumBrowser`.
  - Refactor: Javadoc com o motivo.
  - Verificar: `mvn test -Dtest=SeleniumBrowserTest`.
- [x] **2. `mvn clean verify`.**
- [x] **3. Checkpoint manual**: `scan` contra o DVWA. O stderr não pode ter nenhuma linha `CDP`.

## Resultado

- **Passo 2.** `mvn clean verify`: 401 testes, 0 falhas, BUILD SUCCESS.
- **Passo 3. Checkpoint** em 2026-11-17, lab real: `scan --target http://localhost:8080` sem
  `--module`, com a sessão do DVWA. O stderr caiu de **40 linhas de aviso de CDP** (#58) para
  **0**. Sobraram só as 2 linhas do próprio vulnscan: `erro no módulo ApiAuthModule: …` e
  `Scan incompleto: 3 achado(s), 1 módulo(s) com erro`. O resultado é o mesmo da #58: saída 2,
  3 achados e 1 erro.
