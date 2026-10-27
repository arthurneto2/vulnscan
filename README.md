# vulnscan

Scanner de vulnerabilidades web em Java que detecta falhas de duas formas — por heurística
e com apoio de um modelo de IA local — e **compara empiricamente** as duas. Projeto da
disciplina SIN 451 (Segurança da Informação), UFV Campus Rio Paranaíba.

Três tipos de falha: injeção SQL, XSS (refletido e armazenado) e autenticação/autorização
em API REST. Arquitetura hexagonal; tudo open source e offline.

## Pré-requisitos

- Java 21 (JDK) e Maven
- Docker e Docker Compose (para o laboratório de alvos)
- Ollama, para a camada de IA: https://ollama.com

```bash
ollama pull mistral:7b-instruct
```

## Rodando

```bash
# 1. Subir os alvos vulneráveis
docker compose -f lab/docker-compose.yml up -d

# 2. Compilar
mvn clean verify

# 3. Scan (heurística)
java -jar target/vulnscan.jar scan --target http://localhost:3000 --module xss

# 4. Scan comparando heurística e IA
java -jar target/vulnscan.jar scan --target http://localhost:3000 --detector both

# 5. Experimento completo (heurística vs IA contra o gabarito)
java -jar target/vulnscan.jar benchmark --ground-truth experiments/ground_truth.yaml
```

## Estrutura

- `src/main/java/br/ufv/vulnscan/` — domínio, portas, adaptadores, app e CLI
- `docs/` — arquitetura, contratos e protocolo do experimento (começe por `docs/build-order.md`)
- `lab/` — `docker-compose.yml` dos alvos
- `experiments/` — gabarito e resultados

## Para desenvolver (com Claude Code)

As decisões estão tomadas e documentadas. Abra o Claude Code na raiz do repositório — ele
lê o `CLAUDE.md` automaticamente — e siga `docs/build-order.md` fase por fase.

## Licença

Código sob MIT (ver `LICENSE`). Dependências e modelo são Apache 2.0 / MIT.
