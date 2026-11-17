# Spec — README e portas do laboratório (issue #63)

## Objetivo

Fechar a #63: corrigir e completar o README e restringir as portas do laboratório a
`127.0.0.1`. Hoje o `docker-compose.yml` publica os alvos vulneráveis em todas as interfaces,
apesar de o próprio comentário dizer "nunca expostos".

## Situação de cada item da issue

| # | Item | Situação |
|---|---|---|
| 1 | Digest do modelo vazio | **Já resolvido.** O README tem o digest do `qwen2.5:14b-instruct` (o Mistral 7B saiu na #61). Conferido em 2026-11-17: `sha256:7cdf5a01…` é o manifesto do registro e lista o blob dos pesos `sha256:2049f567…`, o mesmo que o Ollama carrega. |
| 2 | `ollama list --digests` não existe | O README não cita mais o comando, mas também não diz como conferir o digest. **Acrescentar** um comando que funciona no Ollama 0.40. |
| 3 | Passo do `setup.php` do DVWA | **Já resolvido** (#59): o README tem *Create / Reset Database*. |
| 4 | Portas em `0.0.0.0` | **Corrigir**: `127.0.0.1:8080:80` e `127.0.0.1:3000:3000`. |
| 5 | "Java 21" → "Java 21+" | **Corrigir.** O build mira o Java 21 (`release` 21, CI no 21), e todas as rodadas locais desta máquina usaram o Java 25.0.4. |
| 6 | Ollama sem sudo | **Acrescentar** um parágrafo curto: tarball em `~/.local/ollama`, `ollama serve` local. |

## Escopo

1. `lab/docker-compose.yml`: as portas passam a usar `127.0.0.1:` e o comentário explica por
   quê.
2. `LabComposeTest#portasSoEmLocalhost`: toda linha de porta em `ports:` começa com
   `127.0.0.1:`. É o teste que exige a mudança do item 4.
3. README:
   - "Java 21 ou mais novo";
   - o comando para conferir o digest:
     `grep -c sha256:7cdf5a0187d5… ~/.ollama/models/manifests/registry.ollama.ai/library/qwen2.5/14b-instruct`
     deve devolver `1`;
   - o parágrafo da instalação sem sudo;
   - uma nota de que o laboratório só escuta em `127.0.0.1`.

### Fora do escopo

- Trocar o modelo ou o digest.
- Mudar o `CLAUDE.md`. Ele só tem os comandos `up` e `down`, que continuam iguais.

## Contratos tocados

Nenhum.

## Critérios de aceite

1. `LabComposeTest#portasSoEmLocalhost`: falha antes da mudança (`8080:80`) e passa depois.
2. `mvn clean verify` verde.
3. **Checkpoint manual** (lab real):
   - `docker compose down && up -d` com o compose novo;
   - `docker compose ps` mostra `127.0.0.1:8080->80` e `127.0.0.1:3000->3000`;
   - `curl` em `localhost:8080` e `localhost:3000` responde;
   - um `scan` contra o DVWA dá o mesmo resultado de antes: 3 achados e 1 erro;
   - um `scan --module xss` contra o Juice Shop roda.
4. O comando de conferir o digest devolve `1` nesta máquina.

## Referências

Issue #63; `lab/docker-compose.yml`; `LabComposeTest`.
