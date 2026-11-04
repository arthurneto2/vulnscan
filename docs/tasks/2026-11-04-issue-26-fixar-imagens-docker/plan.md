# Plano — Fixar imagens Docker (issue #26)

Spec: `spec.md` nesta pasta. Branch: `issue-26-fixar-imagens-docker`.

## Passos

- [ ] **1. Teste do compose (red).** `src/test/.../lab/LabComposeTest`: lê
  `lab/docker-compose.yml`; falha enquanto houver `:latest` / faltar `@sha256:` nos serviços.
  `mvn test -Dtest=LabComposeTest`.

- [ ] **2. Fixar por digest (green).** Editar o compose com os dois digests; comentário de
  data/atualização.

- [ ] **3. Doc.** Nota em `README.md` e `docs/experiment.md`.

- [ ] **4. Checkpoint.** `mvn clean verify` verde; `docker compose up -d` sobe os alvos.
  Marcar passos, commits, push.
