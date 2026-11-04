# Spec — CI: mvn clean verify no GitHub Actions (issue #27)

Referência: issue #27. Sem dependências. Workflow que roda `mvn clean verify` (Java 21) em
push e pull request; verde na `main`.

## Objetivo

Garantir, a cada push/PR, que a suíte passa — o mesmo `mvn clean verify` dos checkpoints,
sem Docker nem Ollama (os testes são autossuficientes: `HttpServer` do JDK e fakes).

## Escopo

### `.github/workflows/ci.yml`

- Nome: `CI`.
- Gatilhos: `push` na `main` e `pull_request` (qualquer base). Assim um push de branch com PR
  aberto não roda duas vezes.
- Job `build` em `ubuntu-latest`:
  1. `actions/checkout@v4`;
  2. `actions/setup-java@v4` com `temurin` 21 e cache do Maven (`cache: maven`);
  3. `mvn -B -ntp clean verify`.
- `-B` (batch) e `-ntp` (no transfer progress) para log limpo.

### Nota sobre o Selenium no CI

`SeleniumBrowserTest` usa `Assumptions.abort` quando o Chrome não pode iniciar
(`SessionNotCreatedException`). No `ubuntu-latest` há Chrome; se o driver resolver, os 3
testes rodam de verdade, senão são pulados. De qualquer forma a suíte fica verde; o CI não
depende de Docker/Ollama.

## Fora do escopo

- Publicar artefato/release; matriz de SOs/JDKs; cache de imagens Docker; rodar o
  laboratório ou o benchmark no CI (precisam de Docker/Ollama).

## Contratos tocados

Nenhum.

## Critérios de aceite

1. `CiWorkflowTest` (lê `.github/workflows/ci.yml`, offline): existe; dispara em `push` e
   `pull_request`; usa Java `21`; roda `mvn ... clean verify`.
2. `mvn clean verify` verde localmente.
3. (no GitHub) o workflow roda e fica verde na `main` após o merge — verificável só depois do
   PR, no Actions.
