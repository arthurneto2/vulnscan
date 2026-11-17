# Spec — Evidências atualizadas, build-order marcado e release v0.2.0

## Objetivo

Deixar o que vai ser entregue e avaliado coerente com o código atual:

- **Release:** a única versão publicada é a `v0.1.0` (2026-11-04). O `main` tem 77 commits
  depois dela, entre eles as #58, #60, #62, #63, #74 e #77–#83.
- **`docs/evidencias.md`** (2026-11-05), base do relatório técnico, descreve um comportamento
  que mudou. Ele diz que o LLM fora do ar vira "não vulnerável"; desde a #74, vira `ERRO`. A
  tabela é de 14 casos e só tem o LLM fora do ar.
- **`docs/build-order.md`** tem 4 itens com o checkbox `[ ]`, embora estejam implementados.

## Escopo

1. **`docs/evidencias.md` recapturado com o JAR atual**, no lab real e resetado:
   1. **Scan de SQLi no DVWA**: o mesmo comando, com o trecho novo do relatório.
   2. **Scan sem `--module` no DVWA**, o comportamento da #58: saída `2`, relatório parcial e
      o stderr com o erro do `ApiAuthModule`.
   3. **Benchmark**: o `summary.csv` real da rodada de referência pós-#83, com o Qwen 14B no
      ar (`experiments/results/2026-11-17-ref83-ollama-qwen14b/`, 16 casos × 3 execuções).
      A rodada não é refeita: os números são os mesmos de `docs/experiment.md`.
   4. **Falha do LLM vira `ERRO`** (#74): um trecho do `run-1.csv` com a coluna `erro`. Será
      capturado com o Ollama parado, num gabarito de 1 caso, para ser rápido.
2. **`docs/build-order.md`**: marcar os 4 itens.
3. **Versão 0.2.0**:
   - `pom.xml` com `<version>0.2.0</version>`;
   - o exemplo de tag no README passa a `v0.2.0`.
4. **Release**: depois do merge deste PR, criar e enviar a tag `v0.2.0` no `main`. O workflow
   `release.yml` builda, testa e publica o JAR e o `.tar.gz`. A tag é enviada só com o PR
   mergeado, para o release levar os docs atualizados.

### Fora do escopo

- Novas rodadas do experimento.
- Mudar o código de produção ou o workflow de release.

## Contratos tocados

Nenhum.

## Critérios de aceite

1. Toda saída em `docs/evidencias.md` foi capturada com o JAR deste branch, ou vem da rodada
   de referência citada, com a pasta indicada. Nenhuma sessão ou cookie real aparece no
   documento: o valor vira `<sessão>`.
2. `docs/build-order.md` sem `[ ]`.
3. `mvn clean verify` verde e o `target/vulnscan.jar` gerado com a versão 0.2.0.
4. Depois da tag: o workflow `Release` verde e o Release `v0.2.0` publicado no GitHub com
   `vulnscan.jar` e `vulnscan-v0.2.0.tar.gz`.
