# Plano — Fixar imagens Docker (issue #26)

Spec: `spec.md` nesta pasta. Branch: `issue-26-fixar-imagens-docker`.

## Passos

- [x] **1. Teste do compose (red).** `src/test/.../lab/LabComposeTest`: lê
  `lab/docker-compose.yml`; falha enquanto houver `:latest` / faltar `@sha256:` nos serviços.
  `mvn test -Dtest=LabComposeTest`.

- [x] **2. Fixar por digest (green).** Editar o compose com os dois digests; comentário de
  data/atualização.

- [x] **3. Doc.** Nota em `README.md` e `docs/experiment.md`.

- [x] **4. Checkpoint.** `mvn clean verify` verde; `docker compose up -d` sobe os alvos.
  Marcar passos, commits, push.
  - Feito: `mvn clean verify` com 272 testes verdes (`LabComposeTest` inclusa). `docker
    compose up -d` sobe os dois alvos a partir do digest (confirmado via `docker inspect`).
