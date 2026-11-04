# Plano — Expandir ground_truth.yaml (issue #20)

Spec: `spec.md` nesta pasta. Branch: `issue-20-ground-truth`.

## Passos

- [x] **1. Teste de invariantes (red).** `src/test/.../experiments/GroundTruthFileTest`
  lê `experiments/ground_truth.yaml` (Jackson YAML) e valida id único, type válido,
  location não vazio, vulnerable booleano, três tipos presentes e ≥1 pos/≥1 neg por tipo.
  Red: o gabarito de exemplo não cobre tudo. `mvn test -Dtest=GroundTruthFileTest`.

- [x] **2. Expandir o YAML (green).** Escrever os 14 casos validados no lab; green.

- [x] **3. Doc.** `docs/ground-truth.md`: campo opcional `security` e nota do negativo de
  SQLi.

- [x] **4. Checkpoint.** `mvn clean verify` verde. Marcar passos, commits, push.
  - Feito: `mvn clean verify` com 236 testes verdes; `GroundTruthFileTest` (3) valida o
    gabarito expandido (14 casos, 8 positivos / 6 negativos; cada tipo com pos e neg).
    Casos conferidos à mão no lab (DVWA e Juice Shop) em 2026-11-04.
