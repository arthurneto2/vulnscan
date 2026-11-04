# Plano — Decisão: modelo LLM fixado (issue #16)

Spec: `spec.md` nesta pasta. Branch: `issue-16-modelo-llm`.

## Passos

- [ ] **1. Config com Ollama (TDD).** `ConfigTest#defaultsFixaModeloEUrlDoOllama` (red:
  `defaults()` ainda não tem os campos). Green: `Config(httpTimeout, ollamaBaseUrl,
  ollamaModel)` com constantes e `defaults()` atualizado. Ajustar `docs/contracts.md`.
  Verificar: `mvn test -Dtest=ConfigTest`.

- [ ] **2. Documentação.** `docs/llm-contract.md` e `README.md` com a tag fixada e o
  placeholder do digest; remover "ou qwen2.5" do contrato.

- [ ] **3. Checkpoint.** `mvn clean verify` verde. Marcar passos aqui, commits, push.
  (Sem checkpoint de lab: é issue de decisão/config.)
