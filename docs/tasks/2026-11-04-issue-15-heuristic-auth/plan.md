# Plano — HeuristicDetector: regras de autenticação/autorização (issue #15)

Spec: `spec.md` nesta pasta. Branch: `issue-15-heuristic-auth`.

- Produção: `src/main/java/br/ufv/vulnscan/domain/detection/HeuristicDetector.java`.
- Teste: `HeuristicDetectorTest`, com helper novo
  `auth(VulnType, int status, String body, Map<String,String> headers)`.

Cada passo: teste primeiro (red por asserção), mínimo para passar (green), refactor.
Verificação: `mvn test -Dtest=HeuristicDetectorTest`.

## Passos

- [ ] **1. Acesso concedido (200) e bloqueado (401/403).** `#acessoConcedidoEVulneravel`
  (parametrizado BROKEN_AUTH/BROKEN_ACCESS_CONTROL, 200 com corpo), `#bloqueadoNaoEVulneravel`
  (parametrizado 401, 403). Ajustar `#tipoSemRegrasNaoEVulneravel` (hoje cobre os dois tipos
  de auth) para não restar nenhum tipo sem regra. Green: ramo de auth com regras 1 e 3
  (corpo não vazio) e o inconclusivo padrão.

- [ ] **2. Corpo vazio no 2xx.** `#respostaVaziaNaoEVulneravel` (parametrizado `""`, `{}`,
  `[]`, corpo nulo). Green: regra 3 do corpo trivial.

- [ ] **3. Redirecionado para login.** `#redirecionadoParaLoginNaoEVulneravel` (302 +
  `location`), `#redirectSemLoginEInconclusivo` (302 sem login). Green: regra 2.

- [ ] **4. Inconclusivo.** `#statusInconclusivoNaoEVulneravel` (parametrizado 404, 500).
  (Provável que já passe da regra 4; confirmar e anotar desvio se for o caso.)

- [ ] **5. Refactor e Javadoc.** Constantes nomeadas; Javadoc com as regras de auth/acesso.

- [ ] **6. Checkpoint.** `mvn clean verify` verde. Marcar na Fase 4 do `build-order` o item
  das regras de auth/acesso. Marcar passos aqui, commits, push. (Sem checkpoint de lab: é do
  módulo, #14.)
