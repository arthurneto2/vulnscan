# Plano — HeuristicDetector: regras de XSS (issue #13)

Spec: `spec.md` nesta pasta. Branch: `issue-13-heuristic-xss`.

- Produção: `src/main/java/br/ufv/vulnscan/domain/detection/HeuristicDetector.java`.
- Teste: `HeuristicDetectorTest`, com helper novo
  `xss(VulnType, String value, String body, String contentType)`.

Cada passo: teste primeiro (red por asserção), mínimo para passar (green), refactor.
Verificação: `mvn test -Dtest=HeuristicDetectorTest`.

## Passos

- [x] **1. Refletido cru em HTML.** `#xssCruEmHtmlEVulneravel` (parametrizado:
  `XSS_REFLECTED`, `XSS_STORED`), `#xssCruSemContentTypeEVulneravel`. Ajustar
  `#tipoSemRegrasNaoEVulneravel` para só `BROKEN_*`. Green: ramo de XSS com a regra 1 e o
  negativo padrão.

- [x] **2. Não refletido.** `#xssScriptRemovidoNaoEVulneravel` (DVWA `medium`, 0.6),
  `#xssCorpoNuloNaoLanca`, `#xssEmMaiusculasNaoContaComoCru`.
  - *Desvio:* passaram de primeira (o negativo padrão veio no green do passo 1). Mordida
    confirmada trocando a comparação por uma sem diferenciar maiúsculas.

- [x] **3. Fora de HTML.** `#xssCruEmJsonNaoEVulneravel` (0.5, cita o tipo).

- [x] **4. Escapado.** `#xssEscapadoNaoEVulneravel` (corpo do Juice Shop `/redirect`, 0.8).

- [x] **5. Refactor e Javadoc.** Constantes nomeadas; Javadoc com as regras de XSS.

- [ ] **6. Checkpoint.** `mvn clean verify` verde. Lab: `scan --module xss` no DVWA `low` e
  `medium` (1 achado cada) e no Juice Shop (0). Marcar na Fase 3 do `build-order` o item
  das regras de XSS. Marcar passos aqui, commits, push.
