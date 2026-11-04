# Plano — README final e evidências (issue #25)

Spec: `spec.md` nesta pasta. Branch: `issue-25-readme-final`.
Issue de documentação: sem ciclo red→green (não há código de produção).

## Passos

- [ ] **1. Gerar evidências no lab.** Lab no ar + reset do DVWA. `scan --module sqli
  --detector both` no DVWA (trecho do relatório) e `benchmark --runs 1` (summary). Capturar.

- [ ] **2. `docs/evidencias.md`.** Escrever com o trecho de scan e a matriz do benchmark.

- [ ] **3. Expandir o `README.md`.** Laboratório (reset + DVWA/--header), build, scan
  (módulos/detector/formatos), benchmark, documentação, link das evidências.

- [ ] **4. Checkpoint.** `mvn clean verify` verde. Marcar passos, commits, push.
