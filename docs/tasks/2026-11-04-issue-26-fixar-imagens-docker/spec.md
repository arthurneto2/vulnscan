# Spec — Fixar versões das imagens Docker do laboratório (issue #26)

Referência: issue #26. Sem dependências. `docs/experiment.md` exige a **mesma versão das
imagens em todas as execuções**; o `lab/docker-compose.yml` usa `:latest`, que é móvel e
quebra a reprodutibilidade.

## Objetivo

Fixar as imagens do laboratório por **digest** (`name@sha256:...`), para que `docker compose
up` baixe sempre o mesmo bit. Registrar os digests validados.

## Decisão (digests capturados em 2026-11-04, imagens usadas em todos os checkpoints)

- DVWA: `vulnerables/web-dvwa@sha256:dae203fe11646a86937bf04db0079adef295f426da68a92b40e3b181f337daa7`
- Juice Shop: `bkimminich/juice-shop@sha256:42843723a7bf4246aa5d63c324978b17ef296ac78434aeddd34c427bfb5853f4`

Ambos os repositórios publicam só `:latest` como tag móvel; o digest é o pino reprodutível.
Um comentário no compose registra a data e o nome amigável (`:latest` em 2026-11-04).

## Escopo

- `lab/docker-compose.yml`: trocar `image: <repo>:latest` por `image: <repo>@sha256:<digest>`
  nos dois serviços; manter `container_name`, portas e `restart`. Comentário com a data de
  captura e como atualizar (puxar `:latest`, pegar o `RepoDigests` e repinar).
- `README.md`/`docs/experiment.md`: nota curta de que as imagens são fixadas por digest no
  compose (reprodutibilidade).

## Fora do escopo

- CI (#27), atualizar as imagens para versões mais novas. Verificar vulnerabilidades das
  próprias imagens.

## Contratos tocados

Nenhum.

## Critérios de aceite

1. `LabComposeTest` (lê `lab/docker-compose.yml`, offline): cada serviço (`dvwa`,
   `juice-shop`) tem `image:` com `@sha256:` e **nenhum** `:latest`.
2. Os digests acima estão no compose.
3. `mvn clean verify` verde.
4. (manual) `docker compose -f lab/docker-compose.yml up -d` sobe os dois alvos a partir do
   digest.
