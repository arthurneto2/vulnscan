# vulnscan

Scanner de vulnerabilidades web em Java que detecta falhas de duas formas — por heurística
e com apoio de um modelo de IA local — e **compara empiricamente** as duas. Projeto da
disciplina SIN 451 (Segurança da Informação), UFV Campus Rio Paranaíba.

Três tipos de falha: injeção SQL, XSS (refletido e armazenado) e autenticação/autorização
em API REST. Arquitetura hexagonal (ports & adapters); tudo open source e offline.

## Pré-requisitos

- Java 21 (JDK) e Maven
- Docker e Docker Compose (para o laboratório de alvos)
- Para a camada de IA, **um dos dois**:
  - Ollama local (padrão): https://ollama.com, com o modelo abaixo;
  - uma chave de API de um provedor compatível com OpenAI (`--llm api`, ver abaixo).

```bash
ollama pull qwen2.5:14b-instruct
```

Modelo fixado para o experimento (reprodutibilidade): tag `qwen2.5:14b-instruct`
(Q4_K_M, Apache-2.0, ~9 GB), digest `sha256:7cdf5a0187d5c58cc5d369b255592f7841d1c4696d45a8c8a9489440385b22f6`. A tag também está em
`app/Config.java` (`Config.defaults()`).

**Modelo local: mínimo de 14B parâmetros.** Modelos de 7B (Mistral 7B, Qwen2.5 7B) erram de
forma sistemática ao julgar saída com escape de HTML e geram falso positivo fixo; o motivo e
os números estão em `docs/experiment.md` ("Escolha do modelo"). O 14B roda numa GPU de 6 GB
com parte das camadas na CPU (~20 s por veredito); por isso o Ollama tem timeout próprio de
120 s.

### IA via API (sem modelo local)

Para quem não tem máquina para o 14B, o detector `llm` pode usar um modelo hospedado:

1. Crie uma chave gratuita em https://build.nvidia.com (conta NVIDIA Developer e verificação
   de telefone; o plano gratuito é para avaliação e tem limite de ~40 req/min).
2. Exporte a chave **no seu shell** (nunca em arquivo do repositório):
   `export VULNSCAN_LLM_API_KEY=nvapi-...`
3. Rode com `--llm api`, ex.: `java -jar target/vulnscan.jar scan --target http://localhost:3000 --module xss --detector llm --llm api`.

URL e modelo podem ser trocados com `VULNSCAN_LLM_API_URL` e `VULNSCAN_LLM_API_MODEL`
(qualquer API compatível com OpenAI; detalhes em `docs/cli.md`).

> **Aviso de dados:** no plano gratuito a NVIDIA registra o que é enviado e pode usar para
> treinar modelos. O vulnscan só envia requisições e respostas do laboratório local; não use
> `--llm api` contra sistemas com dados reais.

## Laboratório de alvos

```bash
# Subir DVWA (:8080) e Juice Shop (:3000)
docker compose -f lab/docker-compose.yml up -d

# Derrubar
docker compose -f lab/docker-compose.yml down

# Resetar ao estado de fábrica (OBRIGATÓRIO entre execuções do benchmark:
# o XSS armazenado suja o banco)
docker compose -f lab/docker-compose.yml down && docker compose -f lab/docker-compose.yml up -d
```

As imagens do laboratório são **fixadas por digest** no `docker-compose.yml`
(reprodutibilidade — ver `docs/experiment.md`).

O **DVWA exige sessão**: faça login no navegador (`admin`/`password`), escolha o nível em
*DVWA Security* (ex.: `low`) e passe o cookie ao scanner com `--header` (ver abaixo). O
Juice Shop não precisa de login para os módulos de SQLi/XSS; o módulo de API faz o próprio
registro/login.

## Build

```bash
# Compila e roda os testes (checkpoint de cada fase)
mvn clean verify

# Só gera o target/vulnscan.jar (main = br.ufv.vulnscan.cli.Main)
mvn package -DskipTests
```

Nota: o teste do adaptador Selenium (`SeleniumBrowserTest`) é **pulado** se o Chrome não
puder ser iniciado no ambiente; o restante da suíte não depende de navegador nem de rede.

## Download de uma versão pronta

Cada tag `v*` publicada no GitHub gera automaticamente um Release com:

- `vulnscan.jar`: aplicação executável;
- `vulnscan-vX.Y.Z.tar.gz`: JAR, laboratório Docker, gabarito do benchmark e documentação.

Para executar, instale Java 21, baixe `vulnscan.jar` e rode:

```bash
java -jar vulnscan.jar scan --target http://localhost:3000 --module xss
```

O JAR não inclui Java, Docker, as imagens do laboratório, Ollama ou o modelo de IA.
Para usar o laboratório, execute `docker compose -f lab/docker-compose.yml up -d`.
Para usar o detector LLM, instale o Ollama e baixe o modelo fixado neste README.

Para criar uma nova versão, publique uma tag no formato `vX.Y.Z`:

```bash
git tag v0.1.0
git push origin v0.1.0
```

O workflow de Release executará os testes e anexará os dois arquivos à página da versão.

## Scan

```
vulnscan scan --target <url> [--module <nome>]... [--header "<nome>: <valor>"]...
              [--detector heuristic|llm|both] [--format md|json|html] [--out <arquivo>]
```

- `--module`: `sqli`, `xss` (refletido via HTTP), `xss-dom` (XSS de DOM, via navegador),
  `xss-stored` (armazenado, via navegador) e `api` (auth/autorização). Repetível; padrão: todos.
  Os módulos `xss-dom`/`xss-stored` e "todos" abrem o Chrome headless (Selenium).
- `--detector`: `heuristic` (padrão), `llm` ou `both` (roda os dois e reporta os dois vereditos).
- `--format`: `md` (padrão), `json` ou `html`.
- `--header`: repetível; usado para a sessão do DVWA.

```bash
# SQLi no DVWA (com a sessão logada)
java -jar target/vulnscan.jar scan --target http://localhost:8080 --module sqli \
     --header "Cookie: PHPSESSID=<sessão>; security=low"

# XSS no Juice Shop, comparando heurística e IA, relatório HTML
java -jar target/vulnscan.jar scan --target http://localhost:3000 --module xss \
     --detector both --format html --out report.html
```

Detalhes e códigos de saída em `docs/cli.md`.

## Benchmark (o experimento)

Roda a heurística **e** o LLM sobre o gabarito, compara com a verdade conhecida e grava os CSVs.

```
vulnscan benchmark --ground-truth <arquivo> [--runs <n>] [--out <dir>]
```

```bash
# Alvos no estado de fábrica + Ollama no ar
java -jar target/vulnscan.jar benchmark --ground-truth experiments/ground_truth.yaml --runs 3
```

Saída em `--out` (padrão `experiments/results/`): um `run-<n>.csv` por execução (uma linha
por caso × detector) e um `summary.csv` com a matriz de confusão e as métricas (precisão,
recall, F1, taxa de falso positivo, tempo médio) por tipo de falha e no total, para cada
detector. Protocolo e métricas em `docs/experiment.md`.

Exemplos de saída real estão em [`docs/evidencias.md`](docs/evidencias.md).

## Estrutura

- `src/main/java/br/ufv/vulnscan/` — `domain/` (modelos, detecção, módulos), `port/`,
  `adapter/` (http, llm, browser, report), `app/` (composição e casos de uso) e `cli/`
- `docs/` — documentação (comece por `docs/build-order.md`)
- `lab/` — `docker-compose.yml` dos alvos
- `experiments/` — gabarito (`ground_truth.yaml`) e resultados (`results/`, não versionado)

## Documentação

- `docs/architecture.md` — camadas e regra de dependência
- `docs/contracts.md` — interfaces e records (contratos fechados)
- `docs/cli.md` — superfície da CLI
- `docs/ground-truth.md` — formato do gabarito
- `docs/experiment.md` — protocolo e métricas do experimento
- `docs/llm-contract.md` — prompt e saída da camada de IA
- `docs/evidencias.md` — evidências de execução
- `docs/build-order.md` — ordem de implementação

## Licença

Código sob MIT (ver `LICENSE`). Dependências e modelo são Apache 2.0 / MIT.
