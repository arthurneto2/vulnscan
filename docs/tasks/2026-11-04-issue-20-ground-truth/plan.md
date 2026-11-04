# Plano — Expandir ground_truth.yaml (issue #20)

Spec: `spec.md` nesta pasta. Branch: `issue-20-ground-truth`.

## Passos

- [ ] **1. Teste de invariantes (red).** `src/test/.../experiments/GroundTruthFileTest`
  lê `experiments/ground_truth.yaml` (Jackson YAML) e valida id único, type válido,
  location não vazio, vulnerable booleano, três tipos presentes e ≥1 pos/≥1 neg por tipo.
  Red: o gabarito de exemplo não cobre tudo. `mvn test -Dtest=GroundTruthFileTest`.

- [ ] **2. Expandir o YAML (green).** Escrever os 14 casos validados no lab; green.

- [ ] **3. Doc.** `docs/ground-truth.md`: campo opcional `security` e nota do negativo de
  SQLi.

- [ ] **4. Checkpoint.** `mvn clean verify` verde. Marcar passos, commits, push.
