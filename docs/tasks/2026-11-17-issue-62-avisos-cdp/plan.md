# Plano — Silenciar os avisos de CDP do Selenium (issue #62)

Spec: `spec.md` nesta pasta. Branch: `issue-62-avisos-cdp`, a partir do `main` em `942b16c`.

## Passos

- [ ] **1. Loggers de CDP em `SEVERE` (red → green).**
  - Teste: `SeleniumBrowserTest#avisosDeCdpSilenciados`.
  - Red: o nível é `null`, porque é herdado do pai; falha na asserção.
  - Green: campos `static final Logger` com `setLevel(Level.SEVERE)` no `SeleniumBrowser`.
  - Refactor: Javadoc com o motivo.
  - Verificar: `mvn test -Dtest=SeleniumBrowserTest`.
- [ ] **2. `mvn clean verify`.**
- [ ] **3. Checkpoint manual**: `scan` contra o DVWA. O stderr não pode ter nenhuma linha `CDP`.
