# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

# vulnscan — scanner de vulnerabilidades com avaliação empírica de IA

Projeto acadêmico (SIN 451, UFV Rio Paranaíba). Scanner de vulnerabilidades web
próprio, em Java, que detecta falhas de duas formas — **heurística** e **LLM local** —
e **compara empiricamente** as duas. A comparação é o produto central do trabalho.

> Antes de escrever código, leia `docs/build-order.md` e implemente na ordem indicada.
> Os contratos (assinaturas de interfaces e records) estão em `docs/contracts.md` e já
> estão decididos — não os reinvente; preencha os corpos.

## Fluxo de trabalho obrigatório (toda task ou issue)

Nenhum código de produção é escrito sem que antes existam a spec, o plano e um teste
falhando. Vale para tudo: fases do `build-order`, issues, bugs e refatorações.

### 1. Spec e plano antes do código

Cada task/issue ganha uma pasta em `docs/tasks/`, com dois arquivos `.md`:

```
docs/tasks/AAAA-MM-DD-<slug>/
├── spec.md   # o QUÊ e o PORQUÊ
└── plan.md   # o COMO, passo a passo
```

Exemplo: `docs/tasks/2026-10-27-fase-1-http-ponta-a-ponta/`.

- **`spec.md`**: objetivo; escopo e o que fica fora; contratos de `docs/contracts.md`
  tocados; critérios de aceite verificáveis (viram os testes); referência à fase do
  `docs/build-order.md` ou à issue.
- **`plan.md`**: passos pequenos e ordenados. Cada passo diz o teste a escrever primeiro
  (classe e método), o código mínimo para passar e como verificar (`mvn test -Dtest=...`).
  Termina com `mvn clean verify` e o checkpoint da fase.
- Apresente spec e plano ao usuário e **espere aprovação antes de implementar**.
- Se a implementação desviar do plano, atualize `plan.md` (e `spec.md`, se o escopo
  mudar) antes de seguir. Ao concluir, marque os passos feitos no `plan.md`.

### 2. TDD em todo código (red → green → refactor)

1. **Red:** escreva um teste que falhe e rode-o. Confirme que falha **pelo motivo
   esperado** (asserção, não erro de compilação ou de setup).
2. **Green:** escreva o mínimo de código de produção para o teste passar.
3. **Refactor:** limpe com os testes verdes; rode tudo de novo.

- Nada de código de produção sem um teste falhando que o exija. Se escreveu código antes
  do teste, apague e recomece pelo teste.
- Bug: primeiro um teste que reproduz o bug e falha; depois a correção.
- Como testar cada camada sem quebrar as regras de rede/IA:
  - `domain/`: fakes escritos à mão das portas (`HttpClient`, `LlmClient`, `ReportWriter`).
  - `adapter/http` e `adapter/llm`: servidor local em processo
    (`com.sun.net.httpserver.HttpServer`, do JDK) em porta efêmera devolvendo respostas
    fixas — nunca Docker, Ollama ou internet.
  - `adapter/report`: escreva em diretório temporário (`@TempDir`) e verifique o conteúdo.
  - `cli/`: `new CommandLine(new Main()).execute(...)` checando código de saída e saída.

## Comandos

```bash
# Build e testes (checkpoint de cada fase do build-order)
mvn clean verify

# Uma classe de teste / um método (nomes ilustrativos; ainda não há src/test)
mvn test -Dtest=HeuristicDetectorTest
mvn test -Dtest=HeuristicDetectorTest#detectaErroSqlMysql

# Só gerar target/vulnscan.jar (shade, main = br.ufv.vulnscan.cli.Main)
mvn package -DskipTests

# Subir o laboratório de alvos vulneráveis (DVWA em :8080, Juice Shop em :3000)
docker compose -f lab/docker-compose.yml up -d
docker compose -f lab/docker-compose.yml down
# Resetar ao estado de fábrica (obrigatório entre execuções do benchmark: XSS armazenado suja o banco)
docker compose -f lab/docker-compose.yml down && docker compose -f lab/docker-compose.yml up -d

# Rodar um scan (depois de compilar)
java -jar target/vulnscan.jar scan --target http://localhost:3000 --module xss

# Rodar o experimento (heurística vs LLM contra o gabarito)
java -jar target/vulnscan.jar benchmark --ground-truth experiments/ground_truth.yaml

# Pré-requisito da camada de IA: Ollama rodando com o modelo fixado
ollama pull mistral:7b-instruct
```

## Regras de arquitetura (não negociáveis)

- **Hexagonal (ports & adapters).** O domínio (`domain/`) não importa nada de rede,
  disco, Docker, Selenium, Ollama ou Jackson. Ele fala com o mundo externo **só** pelas
  interfaces em `port/`.
- **Regra de dependência:** tudo aponta para dentro. `adapter/` conhece `port/`; `port/`
  e `domain/` nunca conhecem `adapter/`. Se precisar importar um adaptador no domínio, a
  modelagem está errada.
- **Porta vs. estratégia interna.** São portas (bordas externas): `HttpClient`,
  `LlmClient`, `ReportWriter`. NÃO são portas, são polimorfismo interno do domínio:
  `Detector` (heurístico vs. LLM) e `VulnModule` (SQLi/XSS/API).
- **Injeção de dependência manual.** Tudo é ligado na raiz de composição
  (`app/Container.java`). **Não** adicione Spring nem nenhum framework de DI.
- **Não crie portas especulativas.** Uma interface nova só se justifica com duas
  implementações reais ou com necessidade de dublê em teste. Exceção já decidida: o XSS
  armazenado usa Selenium atrás de uma interface pequena **do próprio módulo**, para o
  domínio não depender de Selenium.
- **Mudou assinatura de record/interface? Atualize `docs/contracts.md`** no mesmo passo.
- A CLI só traduz argumentos e delega para `app/ScanService` (sem lógica de scan).
  Códigos de saída: `0` ok, `1` erro de uso, `2` erro de execução.

## Camada de IA (regras de `docs/llm-contract.md` que quebram o experimento se ignoradas)

- `OllamaClient` só fala HTTP (`POST localhost:11434/api/generate`, `stream: false`,
  `format: "json"`, temperatura baixa). Montar prompt e parsear JSON é do `LlmDetector`.
- O `LlmDetector` **nunca lança exceção**: extrai o primeiro bloco `{...}`; se falhar,
  devolve `new Verdict(false, 0.0, "resposta do LLM não parseável", "llm")`. Timeout
  (~30 s) também vira veredito de baixa confiança, não erro fatal.
- Trunque o corpo da resposta HTTP (~4 KB) antes de pôr no prompt.
- Modelo e versão fixados em `app/Config.java` (e no README) — é o que torna o experimento
  reproduzível.

## Convenções de código

- **Java 21.** Use `record` para modelos de valor e `sealed`/`enum` para hierarquias
  fechadas. Modelos do domínio são imutáveis.
- Pacote raiz: `br.ufv.vulnscan`.
- Sem dependência que não esteja no `pom.xml`. Licenças permitidas: Apache 2.0, MIT,
  JDK. Nada de GPL em dependência de runtime.
- O domínio é testável sem rede nem IA: nos testes, as portas entram como dublês (fakes),
  nunca mocks de HTTP real. Só JUnit 5 está no `pom.xml` (sem Mockito) — escreva fakes à
  mão. Teste de unidade nunca sobe Docker nem Ollama.
- Toda classe pública com um Javadoc curto dizendo o que ela faz e em que camada vive.

## Segurança e escopo

- O scanner só aponta para `localhost` / alvos do laboratório. Nunca para a internet.
- Nada de credenciais, chaves ou tokens no repositório. A IA é 100% local (Ollama).
- Só os três tipos de falha do escopo: injeção SQL, XSS (refletido e armazenado) e
  autenticação/autorização em API REST.
- O gabarito (`experiments/ground_truth.yaml`) precisa de casos **negativos** (~metade por
  tipo); sem eles não se mede falso positivo, que é onde se espera que a IA ajude.

## Onde está cada decisão

- Arquitetura e camadas: `docs/architecture.md`
- Contratos (interfaces + records, já fechados): `docs/contracts.md`
- Formato do gabarito do experimento: `docs/ground-truth.md`
- Contrato do prompt e da saída da IA: `docs/llm-contract.md`
- Superfície da CLI: `docs/cli.md`
- Protocolo e métricas do experimento: `docs/experiment.md`
- Ordem de implementação (siga esta): `docs/build-order.md`
- Spec e plano de cada task/issue: `docs/tasks/AAAA-MM-DD-<slug>/`
