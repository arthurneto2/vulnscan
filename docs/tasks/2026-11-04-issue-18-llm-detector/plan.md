# Plano — LlmDetector (issue #18)

Spec: `spec.md` nesta pasta. Branch: `issue-18-llm-detector`.

Fake `LlmClient` que devolve um texto fixo (ou lança). Cada passo: teste → mínimo → refactor.
Verificar: `mvn test -Dtest=LlmDetectorTest`.

## Passos

- [ ] **0. Confirmar Jackson no domínio.** Rodar `mvn test -Dtest=DependencyRuleTest` com um
  import de Jackson no `LlmDetector` (esqueleto). Se passar, segue com Jackson; se barrar,
  parse manual. (Anotar o resultado no plano.)

- [ ] **1. Nome e JSON válido.** `#nomeEllm`, `#judgeNuloLancaNpe`, `#jsonValidoViraVerdict`.
  Green: `name()`, chamar o fake, parse direto do JSON inteiro.

- [ ] **2. JSON cercado de texto.** `#jsonCercadoDeTextoEExtraido`. Green: varredura do
  primeiro bloco `{...}` por profundidade.

- [ ] **3. Parse defensivo.** `#lixoSemJsonNaoEParseavel`, `#semCampoVulnerableNaoEParseavel`
  → `Verdict(false,0.0,"...não parseável","llm")`.

- [ ] **4. Campos.** `#confidenceForaDoIntervaloEGrampeada`,
  `#confidenceERationaleAusentesUsamPadrao`.

- [ ] **5. Falha do LlmClient.** `#clienteQueLancaViraBaixaConfianca` (timeout/indisponível).

- [ ] **6. Prompt.** `#promptCitaTipoPayloadRequisicaoETruncaCorpo`. Green: montar o prompt e
  truncar em `MAX_BODY_CHARS`. Javadoc.

- [ ] **7. Checkpoint.** `mvn clean verify` verde. Marcar passos, commits, push.
