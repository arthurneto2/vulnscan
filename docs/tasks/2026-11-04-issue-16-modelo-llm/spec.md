# Spec — Decisão: modelo LLM fixado (issue #16)

Referência: issue #16 (Fase 5 de `docs/build-order.md`). Sem dependências. Registra a escolha
do modelo e fixa a versão exata, o que torna o experimento reproduzível.

## Decisão (2026-11-04)

- **Modelo:** `mistral:7b-instruct` (Apache-2.0), rodado localmente via Ollama. É o já citado
  como padrão no `CLAUDE.md` e no `docs/llm-contract.md`.
- **Tag exata fixada:** `mistral:7b-instruct-v0.3-q4_0` (instruct v0.3, quantização Q4_0).
- **Reprodutibilidade:** a tag fica no código (`app/Config`). O digest `sha256` do blob do
  Ollama (saída de `ollama show --modelfile` / `ollama list --digests`) fica registrado no
  README e no `llm-contract.md`. Como o ambiente de CI/dev não tem Ollama, o digest entra
  como `sha256:<preencher após ollama pull>` até a primeira execução local.
- **Endpoint:** `POST http://localhost:11434/api/generate` (base `http://localhost:11434`).

## Escopo

### `app/Config`

Ganha a configuração do Ollama, lida pela raiz de composição e, adiante, pelo `OllamaClient`
(#17) e `LlmDetector` (#18):

```java
public record Config(java.time.Duration httpTimeout, java.net.URI ollamaBaseUrl, String ollamaModel) {
    public static Config defaults() { ... }   // 30 s, http://localhost:11434, mistral:7b-instruct-v0.3-q4_0
}
```

- Constantes nomeadas para a URL e o modelo. `defaults()` segue sem argumentos.
- Os usos atuais passam por `Config.defaults()`; só o corpo de `defaults()` muda.

### Documentação

- `docs/llm-contract.md`: trocar "`mistral:7b-instruct` (ou `qwen2.5:7b-instruct`)" pela
  decisão fixada (tag + nota do digest); registrar a tag e o placeholder do digest.
- `README.md`: `ollama pull mistral:7b-instruct-v0.3-q4_0`; registrar a tag e o digest
  (placeholder) na seção de IA/reprodutibilidade.
- `docs/contracts.md`: atualizar a assinatura de `app/Config`.

## Fora do escopo

- `OllamaClient` (#17) e `LlmDetector` (#18): os parâmetros de chamada (temperatura,
  `format: json`, `stream: false`, timeout do LLM) entram com eles. Aqui só modelo + URL.
- Baixar o modelo ou medir desempenho.

## Contratos tocados

- `app/Config` (assinatura) em `docs/contracts.md`.

## Critérios de aceite

1. `ConfigTest`: `defaults()` tem `ollamaBaseUrl == http://localhost:11434` e
   `ollamaModel == mistral:7b-instruct-v0.3-q4_0`; `httpTimeout` segue 30 s.
2. `docs/llm-contract.md` e `README.md` citam a tag fixada e o placeholder do digest; não
   resta a forma genérica "ou qwen2.5".
3. `mvn clean verify` verde, incluindo `DependencyRuleTest`.
