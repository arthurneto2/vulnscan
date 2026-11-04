# Plano — CI no GitHub Actions (issue #27)

Spec: `spec.md` nesta pasta. Branch: `issue-27-ci-github-actions`.

## Passos

- [x] **1. Teste do workflow (red).** `src/test/.../ci/CiWorkflowTest`: lê
  `.github/workflows/ci.yml`; exige `on: push`+`pull_request`, Java `21` e `clean verify`.
  `mvn test -Dtest=CiWorkflowTest`.

- [x] **2. Workflow (green).** Criar `.github/workflows/ci.yml` (checkout, setup-java 21 +
  cache maven, `mvn -B -ntp clean verify`).

- [x] **3. Checkpoint.** `mvn clean verify` verde. Marcar passos, commits, push. O verde na
  `main` do Actions confirma-se após o merge (fora deste ambiente).
