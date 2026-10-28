# Plano — HeuristicDetector: regras de SQL injection (issue #9)

Spec: `spec.md` nesta pasta. Branch: `issue-9-heuristic-sqli`.

- Produção: `src/main/java/br/ufv/vulnscan/domain/detection/HeuristicDetector.java`.
- Teste: `src/test/java/br/ufv/vulnscan/domain/detection/HeuristicDetectorTest.java`, com
  helper `evidence(VulnType, String payloadId, String body, long elapsedMillis)` montando
  `Payload`, `HttpRequest` e `HttpResponse` fixos.

Cada passo: teste primeiro (red por asserção), mínimo para passar (green), refactor.
Verificação: `mvn test -Dtest=HeuristicDetectorTest`.

## Passos

- [ ] **1. Esqueleto + nome.**
  - `HeuristicDetector` com `name()` devolvendo `""` e `judge` devolvendo
    `Verdict(false, 0.0, "", "")` (compila, red por asserção).
  - Teste: `#nomeEHeuristic`. Green: `"heuristic"`.

- [ ] **2. Caso negativo.**
  - Testes: `#corpoLimpoNaoEVulneravel` (0.6, `detector == "heuristic"`),
    `#corpoNuloNaoLanca`, `#evidenciaNulaLancaNpe`.
  - Green: ramo `SQL_INJECTION` com o veredito negativo; `requireNonNull`; corpo nulo → `""`.

- [ ] **3. Erro de MySQL.**
  - Teste: `#erroMysqlEVulneravel` (0.9, justificativa cita MySQL).
  - Green: primeira assinatura (padrão por SGBD num `Map` ordenado de `Pattern`).

- [ ] **4. Demais SGBDs e maiúsculas.**
  - Testes: `#erroDeOutrosSgbdsEVulneravel` (parametrizado: SQLite, PostgreSQL,
    `SQLSTATE[`), `#assinaturaIgnoraMaiusculas`.
  - Green: restantes das assinaturas da spec; `CASE_INSENSITIVE`.

- [ ] **5. Atraso temporal.**
  - Testes: `#payloadTemporalLentoEVulneravel` (5000 ms → 0.8),
    `#payloadTemporalAbaixoDoLimiarNaoEVulneravel` (4999 ms),
    `#lentidaoSemPayloadTemporalNaoConta` (8000 ms, id comum).
  - Green: prefixo `sqli-time-` e limiar de 5000 ms.

- [ ] **6. Tipos sem regras.**
  - Teste: `#tipoSemRegrasNaoEVulneravel` (parametrizado pelos 4 tipos não-SQLi, corpo
    com erro de MySQL → `false`, 0.0, justificativa cita o tipo).
  - Green: `switch` no tipo; ramo padrão.

- [ ] **7. Refactor e Javadoc** (camada: domínio, estratégia `Detector`; documentar a
  convenção `sqli-time-` e o limiar). Constantes nomeadas para as confianças.

- [ ] **8. Checkpoint.** `mvn clean verify` verde (inclui `DependencyRuleTest`). Marcar na
  Fase 2 de `docs/build-order.md` os itens do `HeuristicDetector` e do teste de unidade
  (o checkpoint da fase fica para a #10). Marcar passos aqui, commits, PR com "Closes #9".
