# Spec — SqlInjectionModule e arquivo de payloads (issue #10)

Referência: issue #10 (Fase 2 de `docs/build-order.md`): "`domain/module/SqlInjectionModule`:
payloads de aspas, booleano e temporal; captura a `Evidence` e chama o `Detector`" e
"`payloads/sqli.txt` com os payloads". Depende de #8 (fumaça) e #9 (`HeuristicDetector`).

## Objetivo

Primeiro módulo real: injeta payloads de SQLi em pontos conhecidos do alvo, monta a
`Evidence` de cada troca HTTP e pede o veredito ao `Detector`. Os vereditos positivos viram
`Finding` no relatório. Substitui o módulo de fumaça.

## Escopo

### 1. Headers do alvo (`--header`) — decisão: opção repetível na CLI

A página de SQLi do DVWA exige sessão (`Cookie: PHPSESSID=...; security=low`). O login
continua manual (navegador); o scanner só repassa os headers.

- `Target` ganha `Map<String, String> headers` (headers enviados em toda requisição ao
  alvo). Construtor secundário `Target(name, baseUrl)` → headers vazios. Nulo → vazio;
  cópia imutável.
- `ScanRequest` ganha `Map<String, String> headers` (construtor secundário de 4 argumentos
  → vazio). `ScanService` passa os headers para o `Target`.
- CLI: `--header "<Nome>: <valor>"`, repetível. Sem `:` ou nome vazio → saída 1. Nome
  repetido → valores juntados por `", "` (regra de `docs/contracts.md`).

### 2. Arquivo de payloads

- `src/main/resources/payloads/sqli.txt` (vai no jar). Uma linha por payload:
  `id<TAB>valor<TAB>descrição`. Linhas vazias e começadas por `#` são ignoradas.
- Payloads, na ordem de execução (rápidos primeiro, temporal por último):
  - aspas: `sqli-quote-single` (`'`), `sqli-quote-double` (`"`);
  - booleano: `sqli-bool-or-true` (`' OR '1'='1`), `sqli-bool-or-comment` (`1' OR 1=1-- -`);
  - temporal (prefixo `sqli-time-`, convenção da #9): `sqli-time-mysql-sleep`
    (`1' AND SLEEP(5)-- -`).
- `domain/module/PayloadFile.parse(String content, VulnType type)` → `List<Payload>`.
  Puro (sem I/O). Linha malformada (≠ 3 campos, campo vazio) ou id repetido →
  `IllegalArgumentException` citando o número da linha.
- Quem lê o recurso do classpath é o `Container` (app); recurso ausente → erro na
  montagem.

### 3. `domain/module/InjectionPoint` e `SqlInjectionModule`

- `record InjectionPoint(String path, String parameter)`: requisição `GET` em
  `path` (relativo à URL base; pode trazer query fixa, ex.: `?Submit=Submit`) com o
  payload no parâmetro `parameter` da query.
- `SqlInjectionModule(HttpClient http, Detector detector, List<Payload> payloads,
  List<InjectionPoint> points)`. Nulos → NPE; listas vazias ou payload de outro tipo →
  `IllegalArgumentException`. `type()` → `SQL_INJECTION`.
- `scan(target)`: para cada ponto, para cada payload (na ordem):
  1. URL = `baseUrl` resolvida com `path` + `parameter=<valor URL-encoded UTF-8>`
     (com `?` ou `&`, conforme o `path` já tenha query);
  2. `HttpRequest("GET", url, target.headers(), null)`, enviado pela porta;
  3. `Evidence(payload, request, response)` → `detector.judge(...)`;
  4. vulnerável → `Finding(SQL_INJECTION, HIGH, target, evidence, verdict)` e **passa para
     o próximo ponto** (não gasta os payloads restantes, em especial o temporal de 5 s).
- Erro do `HttpClient` propaga (o scan não sai "limpo" com o alvo fora do ar).
- Pontos registrados no `Container`: DVWA `/vulnerabilities/sqli/?Submit=Submit` com
  `id`; Juice Shop `/rest/products/search` com `q`. Os dois rodam em qualquer alvo (num alvo
  sem a rota volta 404 e a heurística diz "não vulnerável").

### 4. Composição

- `Container` registra `sqli` → `SqlInjectionModule(httpClient, new HeuristicDetector(),
  payloads de sqli.txt, pontos acima)`. O `smoke` sai (`SmokeModule` e teste apagados).

## Fora do escopo

- SQLi booleana **diferencial** (comparar resposta verdadeira × falsa): uma `Evidence` só
  carrega uma troca. Os payloads booleanos são julgados um a um; a heurística em geral não
  os detecta — limitação que o experimento mede.
- Descoberta de pontos (crawler), POST/JSON, login automático, `--detector` (Fase 5).
- Payload temporal para SQLite (não tem `SLEEP`).

## Contratos tocados (`docs/contracts.md` e `docs/cli.md` atualizados no mesmo passo)

- `Target` (+ `headers`), `ScanRequest` (+ `headers`).
- Novos: `InjectionPoint`, construtor de `SqlInjectionModule`, `PayloadFile.parse`.
- CLI: `--header`.

## Critérios de aceite

1. `Target`: construtor de 2 argumentos → headers vazios; headers copiados e imutáveis.
2. `PayloadFile`: lê linhas válidas; ignora vazias e `#`; erro com nº da linha em linha
   malformada e id repetido. O `sqli.txt` real carrega ≥ 5 payloads, com aspas,
   booleano e um `sqli-time-*`.
3. `SqlInjectionModule` com `HttpClient` e `Detector` fakes (sem rede):
   - envia `GET` com o payload codificado no parâmetro, respeitando query fixa;
   - envia os headers do alvo;
   - `Evidence` passada ao detector tem payload, requisição e resposta certos;
   - nenhum positivo → lista vazia, todos os payloads tentados em todos os pontos;
   - positivo → um `Finding` HIGH por ponto, para nos payloads restantes daquele ponto;
   - erro do `HttpClient` propaga; validações do construtor.
   - com o `HeuristicDetector` real e resposta com erro de MySQL → um `Finding`.
4. `ScanService` passa `headers` ao `Target`; CLI aceita `--header` (repetível), rejeita
   formato inválido com saída 1.
5. `Container`: `sqli` registrado; contra servidor em processo que devolve erro de MySQL
   no ponto do DVWA → relatório com achado de `SQL_INJECTION`.
6. `mvn clean verify` verde, incluindo `DependencyRuleTest`.
7. **Checkpoint manual (laboratório)**: com DVWA em `:8080`, logado e `security=low`,
   `java -jar target/vulnscan.jar scan --target http://localhost:8080 --module sqli
   --header "Cookie: PHPSESSID=<sessão>; security=low"` gera `report.md` com o achado e a
   evidência. Não roda neste container (sem daemon Docker); fica para execução local.
