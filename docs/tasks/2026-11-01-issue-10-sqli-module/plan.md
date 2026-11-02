# Plano — SqlInjectionModule e arquivo de payloads (issue #10)

TDD red → green → refactor, passos pequenos. Branch `issue-10-sqli-module`. Os testes de
unidade usam fakes à mão de `HttpClient` e `Detector` (CLAUDE.md); nada de rede/Docker.

Fakes de teste (escritos no passo 1, reusados nos demais):
- `ScriptedHttpClient`: devolve respostas de uma fila/por casamento de URL e **grava** os
  `HttpRequest` recebidos (para assertar método, URL, headers). Ponto de extensão para
  roteirizar a resposta do `login.php` (com `user_token` no corpo e `Set-Cookie`).
- `ScriptedDetector`: `name()` fixo; `judge` devolve `Verdict`s de uma fila (ou por
  predicado) e grava as `Evidence` recebidas.

---

## Passo 1 — ✅ feito — Esqueleto do módulo: `type()` e null-checks (red → green)

- **Teste primeiro** (`SqlInjectionModuleTest`):
  - `typeEhSqlInjection` → espera `VulnType.SQL_INJECTION` (crit. 1).
  - `construtorRejeitaNulos` → `assertThrows(NullPointerException.class, …)` para cada
    argumento nulo; `scanRejeitaTargetNulo` (crit. 2).
  - Escreve aqui os fakes `ScriptedHttpClient`/`ScriptedDetector` (ainda sem roteiro).
  - Rodar `mvn test -Dtest=SqlInjectionModuleTest` → **red** (classe não existe).
- **Código mínimo**: `domain/module/SqlInjectionModule` com campos finais
  `http`, `detector`, `payloads` (lista copiada), `Objects.requireNonNull` em cada um;
  `type()` devolve `SQL_INJECTION`; `scan(target)` faz `requireNonNull(target)` e devolve
  `List.of()`.
- Verificar: `mvn test -Dtest=SqlInjectionModuleTest` → **green**.

## Passo 2 — ✅ feito — Enviar um payload e virar `Finding` (red → green)

- **Teste primeiro**:
  - `payloadVulneravelViraFinding`: 1 payload; `ScriptedHttpClient` devolve resposta de
    ataque qualquer; `ScriptedDetector` devolve `Verdict(true, 0.9, "…", "heuristic")`.
    Espera 1 `Finding` com `type == SQL_INJECTION`, `severity == CRITICAL`, `target`
    ecoado, `verdict` e `evidence` os esperados; `evidence.payload()` == payload (crit. 5, 6).
  - `payloadNegativoNaoViraFinding`: `Verdict(false, …)` → `scan` devolve lista vazia.
- **Código mínimo**: em `scan`, para cada payload: monta `HttpRequest` GET para
  `target.baseUrl()` (ponto de injeção ainda simplificado, sem login), chama `http.send`,
  monta `Evidence`, chama `detector.judge`; se `verdict.vulnerable()`, cria `Finding` com
  `Severity.CRITICAL` (constante nomeada). Sem login ainda.
- Verificar: `mvn test -Dtest=SqlInjectionModuleTest` → **green**.

## Passo 3 — ✅ feito — Ponto de injeção: URL e parâmetro `id` (red → green)

- **Teste primeiro**:
  - `cadaPayloadGeraUmaRequisicaoNaOrdem`: N payloads, `ScriptedDetector` todos negativos;
    assere N requisições de ataque gravadas, na ordem, cada uma GET para
    `…/vulnerabilities/sqli/` com o `value` do payload no parâmetro `id` (URL-encoded) e
    `Submit=Submit` (crit. 4).
  - `detectorChamadoPorPayload`: assere N chamadas ao detector, `evidence.payload()` na
    ordem (crit. 5).
- **Código mínimo**: constante com o path `/vulnerabilities/sqli/`; monta a query
  `?id=<encode(value)>&Submit=Submit` resolvida sobre `target.baseUrl()`; URL-encode do
  value (`java.net.URLEncoder`, ou construção de `URI` segura).
- Verificar: `mvn test -Dtest=SqlInjectionModuleTest` → **green**.

## Passo 4 — ✅ feito — Login no DVWA e header de sessão (red → green)

- **Teste primeiro**:
  - `fazLoginEMandaCookieDeSessao`: `ScriptedHttpClient` roteiriza GET `login.php` →
    corpo com `name='user_token' value='TOKEN'` e `Set-Cookie: PHPSESSID=SID; path=/`;
    POST `login.php` → 302. Assere: (a) a 1ª requisição é GET `…/login.php`; (b) existe um
    POST `…/login.php` cujo corpo contém `username=admin`, `password=password`,
    `user_token=TOKEN`, `Login=Login`; (c) as requisições de ataque carregam
    `Cookie: PHPSESSID=SID; security=low` (crit. 3).
  - `listaDePayloadsVaziaNaoAtaca`: payloads vazios → nenhum `Finding` (login pode nem
    ocorrer, conforme implementação) (crit. 8).
- **Código mínimo**: helper privado `estabelecerSessao(target)`:
  GET `login.php`, extrai `PHPSESSID` do header `set-cookie` (regex) e `user_token` do
  corpo (regex); POST `login.php` com corpo `application/x-www-form-urlencoded` e header
  `Cookie: PHPSESSID=…`; devolve a string de cookie `PHPSESSID=…; security=low` guardada
  para os ataques. Constantes nomeadas para usuário/senha de laboratório e nomes de
  campos. Javadoc explicando que é o handshake do DVWA (laboratório).
  - Decidir no verde: só estabelecer sessão se `payloads` não estiver vazio.
- Verificar: `mvn test -Dtest=SqlInjectionModuleTest` → **green**.

## Passo 5 — ✅ feito — Arquivo de payloads + carregador no `app/` (red → green)

- **Teste primeiro** (`PayloadLoaderTest`):
  - `parseIgnoraComentariosEBranco`: entrada String multilinha com `#`/linhas vazias →
    só os payloads válidos; `type == SQL_INJECTION`; `id`/`value`/`description` corretos,
    espaços internos do `value` preservados (crit. 9).
  - `recursoRealCarrega`: carrega `payloads/sqli.txt` do classpath → lista não vazia; ao
    menos um id `sqli-time-*`; ao menos um payload de aspas/erro; sem id repetido (crit. 10).
  - Rodar → **red**.
- **Código mínimo**:
  - `src/main/resources/payloads/sqli.txt` com os payloads (aspas/erro + `sqli-time-*`
    temporais; booleanos anotados em comentário — ver spec, decisão 3).
  - `app/PayloadLoader`: método puro `parse(Stream<String>/List<String>, VulnType)` →
    `List<Payload>`; método `load(resourcePath, VulnType)` lê o recurso do classpath
    (`getResourceAsStream`, UTF-8) e delega. Javadoc com o formato TAB-separado.
- Verificar: `mvn test -Dtest=PayloadLoaderTest` → **green**.

## Passo 6 — ✅ feito — Ligar no `Container` e tirar o `smoke` do mapa (red → green)

- **Teste primeiro** (`ContainerTest`, ajuste):
  - `registraSqliEResolveModulo`: `container.scanService()` tem `sqli` disponível e um
    scan com `--module sqli` seleciona um módulo cujo `type() == SQL_INJECTION`; ou
    assere diretamente o mapa de módulos, conforme o `ContainerTest` já faz hoje.
  - `naoRegistraMaisSmoke`: `smoke` não está entre os módulos.
  - Ajustar expectativas existentes do `ContainerTest`/`ScanServiceTest`/`MainTest` que
    dependiam de `smoke` como módulo default.
  - Rodar → **red**.
- **Código mínimo**: em `Container`, carregar `PayloadLoader.load("payloads/sqli.txt",
  SQL_INJECTION)` e registrar `sqli` →
  `new SqlInjectionModule(httpClient, new HeuristicDetector(), payloads)`; remover a
  entrada `smoke` do mapa. Atualizar o Javadoc do `Container` (não fala mais em "smoke
  temporário"). Manter `SmokeModule` e `SmokeModuleTest` no código.
- Verificar: `mvn test -Dtest=ContainerTest,ScanServiceTest,MainTest` → **green**.

## Passo 7 — ✅ feito — Refactor + verificação final

- Refactor com tudo verde: extrair constantes (path, campos de login, severidade,
  limites), Javadoc curto em toda classe pública nova (`SqlInjectionModule`,
  `PayloadLoader`), conferir imutabilidade e null-checks.
- Conferir que o domínio não referencia `app`/`adapter`/`cli` nem lê disco
  (o `PayloadLoader` fica em `app/`): `DependencyRuleTest` (crit. 12).
- Rodar **`mvn clean verify`** → tudo verde (crit. 12).

## Passo 8 — ✅ feito — Checkpoint da Fase 2 (manual, laboratório) + build-order

> Checkpoint atingido em 2026-11-01: `scan --target http://localhost:8080 --module sqli`
> gravou 3 achados de SQL_INJECTION no DVWA (`vulnerables/web-dvwa:latest`, MariaDB):
> `sqli-quote-1` por assinatura de erro (0.90) e os dois temporais `sqli-time-mysql-*`
> por atraso (0.80). Login do módulo OK. Payloads booleanos/union voltaram negativos —
> limitação conhecida (booleana adiada; ver spec).


- `mvn package -DskipTests` → `target/vulnscan.jar`.
- `docker compose -f lab/docker-compose.yml up -d`; aguardar o DVWA subir; na primeira vez
  o DVWA pede "Create / Reset Database" em `/setup.php` — documentar no comentário do PR
  se foi necessário.
- `java -jar target/vulnscan.jar scan --target http://localhost:8080 --module sqli` →
  confere `report.md` com `Finding` de SQL_INJECTION; copiar o trecho como evidência
  (crit. 13).
- Marcar no `docs/build-order.md` os itens da Fase 2 referentes a esta issue
  (`SqlInjectionModule`, `payloads/sqli.txt`, checkpoint).
- Marcar os passos feitos neste `plan.md`.

## PR

- Branch `issue-10-sqli-module`; commits pequenos (um por passo). PR com `Closes #10`,
  resumo e a evidência do checkpoint colada. Usuário faz o merge (merge commit, sem
  squash).
