# Ordem de implementação

Implemente nesta ordem. Cada fase entrega algo que compila e roda, com teste. Não pule
para a IA antes da base heurística funcionar. Marque o checkpoint de cada fase antes de
seguir.

> Convenção: ao terminar uma fase, rode `mvn clean verify` e confirme o checkpoint.
> Commits pequenos, um por passo lógico.
>
> Antes de cada fase (ou issue): `spec.md` + `plan.md` em `docs/tasks/AAAA-MM-DD-<slug>/`,
> aprovados, e implementação em TDD. Detalhes em `CLAUDE.md` → "Fluxo de trabalho
> obrigatório".

## Fase 0 — Esqueleto que compila

- [ ] `pom.xml` com Java 21 e as dependências (já previstas); plugin shade para gerar
      `target/vulnscan.jar`.
- [ ] Os `record` e `enum` de `domain/model/` (assinaturas em `docs/contracts.md`).
- [ ] As interfaces `Detector`, `VulnModule`, `HttpClient`, `LlmClient`, `ReportWriter`.
- **Checkpoint:** `mvn compile` passa.

## Fase 1 — Caminho HTTP ponta a ponta

- [x] `adapter/http/JdkHttpClient` implementando `HttpClient` com `java.net.http`.
- [x] `domain/Scanner` que roda uma lista de `VulnModule` e junta os `Finding`.
- [x] `adapter/report/MarkdownReportWriter`.
- [x] `app/Container` e `app/ScanService`; `cli/Main` com o subcomando `scan`.
- [x] Um `VulnModule` trivial de fumaça (faz uma requisição e não acha nada) só para fechar
      o circuito.
- **Checkpoint:** `vulnscan scan --target http://localhost:3000` roda e grava um `report.md`.

## Fase 2 — Detecção heurística + primeiro módulo real (SQLi)

- [x] `domain/detection/HeuristicDetector`: regras/regex por `VulnType`.
- [x] `domain/module/SqlInjectionModule`: payloads de aspas, booleano e temporal; captura a
      `Evidence` e chama o `Detector`.
- [x] `payloads/sqli.txt` (em `src/main/resources/payloads/`) com os payloads.
- [x] Teste de unidade do `HeuristicDetector` com `Evidence` fixa (sem rede).
- **Checkpoint:** SQLi detectado no DVWA, com evidência no relatório; teste verde. ✅
  (2026-11-04: DVWA `low` e Juice Shop → 1 achado cada; DVWA `impossible` → 0.)

## Fase 3 — XSS (refletido e armazenado)

- [x] `XssModule` com marcador único; refletido via `HttpClient` (DVWA; o XSS de DOM do Juice Shop vai com o Selenium, #12).
- [x] XSS armazenado via Selenium, isolado atrás de uma pequena interface própria do
      módulo (para o domínio não depender de Selenium diretamente). `XssBrowser` +
      `SeleniumBrowser`; também o XSS de DOM do Juice Shop (`xss-dom`).
- [x] Regras de XSS no `HeuristicDetector`.
- **Checkpoint:** XSS refletido e armazenado detectados no Juice Shop. ✅ em parte
  (2026-11-04): refletido no DVWA (`xss`) e de DOM no Juice Shop (`xss-dom`); armazenado no
  DVWA (`xss-stored`, guestbook). Armazenado no Juice Shop exige login/captcha/JWT → issue nova.

## Fase 4 — API REST (autenticação/autorização)

- [x] `ApiAuthModule`: acesso sem token, com token de outro usuário (IDOR) e papel
      indevido. Login do Juice Shop atrás de `ApiSession` (adaptador `JuiceShopSession`).
- [x] Regras de auth/acesso no `HeuristicDetector` (ex.: 200 + dado sensível onde se
      esperava 401/403).
- **Checkpoint:** acesso indevido e IDOR detectados. ✅ (2026-11-04) `scan --module api`
  no Juice Shop → 3 achados: BROKEN_AUTH (admin-config sem token), BROKEN_ACCESS_CONTROL
  (IDOR de cesta e `/api/Users` com usuário comum). Negativos (sem token → 401, papel → 403)
  sem achado.

## Fase 5 — Camada de IA

- [x] `adapter/llm/OllamaClient` implementando `LlmClient` (ver `docs/llm-contract.md`).
- [x] `domain/detection/LlmDetector`: monta o prompt, chama o `LlmClient`, parse defensivo
      para `Verdict`.
- [x] `app/Config` com modelo e versão fixados; `--detector llm|both` na CLI.
- [x] Teste do `LlmDetector` com um `LlmClient` fake que devolve um JSON conhecido.
- **Checkpoint:** o mesmo caso julgado por heurística e por LLM, com justificativa. ✅ em
  parte: a ligação roda cada caso com cada detector (teste `bothRodaOsDoisPorCaso`; `scan
  --detector both` executa os dois). O veredito de LLM real e a comparação por caso (com
  negativos) exigem Ollama no ar (`ollama serve` + `ollama pull`) e são medidos pelo
  BenchmarkRunner (#21); sem Ollama o lado LLM responde "indisponível".

## Fase 6 — Experimento

- [ ] `experiments/ground_truth.yaml` com casos positivos E negativos dos três tipos.
- [x] `BenchmarkRunner`: lê o gabarito, roda cada caso com cada detector N vezes, compara
      com a verdade, acumula VP/FP/VN/FN, calcula métricas, grava os CSVs.
- [ ] Subcomando `benchmark` na CLI.
- **Checkpoint:** `summary.csv` com matriz de confusão e métricas dos três tipos. ✅ em parte
  (2026-11-04): gerado no lab com o detector heurístico (8 VP / 6 VN, 0 FP / 0 FN nos 14
  casos). O lado LLM exige Ollama no ar; o subcomando `benchmark` da CLI é a #22.

## Fase 7 — Acabamento

- [ ] `JsonReportWriter` e `HtmlReportWriter`.
- [ ] README completo e evidências de execução para o relatório.
- [ ] Revisão da regra de dependência (nada de `adapter` importado em `domain`/`port`).

## Testes — princípio

O domínio é testado sem rede nem IA. `HttpClient` e `LlmClient` entram como fakes que
devolvem `HttpResponse`/texto fixos. Não suba Docker nem Ollama em teste de unidade —
isso é para a execução manual e para o experimento.
