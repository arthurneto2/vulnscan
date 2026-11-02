# Spec — SqlInjectionModule e arquivo de payloads (issue #10)

Referência: issue #10 e Fase 2 de `docs/build-order.md`:
"`domain/module/SqlInjectionModule`: payloads de aspas, booleano e temporal; captura a
`Evidence` e chama o `Detector`" e "`payloads/sqli.txt` (ou similar) com os payloads".
O `HeuristicDetector` (#9) já julga SQLi por assinatura de erro de SGBD e por atraso
temporal (`sqli-time-*`); esta issue entrega o primeiro módulo **real**, que fecha o
checkpoint da Fase 2 (SQLi detectado no DVWA, com evidência no relatório).

## Objetivo

Ter um `VulnModule` que, dado um alvo, aplica payloads de SQL injection num ponto de
injeção conhecido do laboratório (DVWA), captura cada troca HTTP como `Evidence`, pede o
veredito ao `Detector` injetado e transforma cada veredito **vulnerável** num `Finding`.
É o primeiro caminho ponta a ponta de detecção real: CLI → ScanService → Scanner →
SqlInjectionModule → HttpClient + Detector → relatório.

## Decisões (desta sessão)

1. **Sessão do DVWA no próprio módulo.** O DVWA exige login autenticado e o cookie
   `security=low` antes de `/vulnerabilities/sqli/`. O módulo faz o handshake sozinho
   (GET `login.php` → extrai `user_token` do HTML e o `PHPSESSID` do `Set-Cookie`; POST
   `login.php` com `admin/password`), e daí em diante manda `Cookie: PHPSESSID=…;
   security=low`. O comando `scan` roda sem passo manual no navegador. Nenhum contrato
   muda.
2. **Ponto de injeção fixo no módulo.** O módulo conhece o ponto do DVWA
   (`/vulnerabilities/sqli/`, parâmetro `id`, com `Submit=Submit`), montado sobre
   `target.baseUrl()`. Sem crawler, sem opção nova de CLI. Mais pontos (ou outros alvos)
   ficam para issues futuras.
3. **Booleana adiada.** Uma `Evidence` carrega uma troca só, então o `HeuristicDetector`
   não compara "condição verdadeira" com "condição falsa". Os payloads de aspas/erro e os
   temporais (que o detector já julga) entram agora; os booleanos entram no arquivo como
   comentário/anotação, e a **comparação** booleana vira issue de follow-up. Nenhuma
   lógica de veredito sai do `Detector` para o módulo (isso distorceria a comparação
   heurística × LLM, que é o produto do trabalho).
4. **Arquivo de payloads lido no `app/`.** Por convenção do CLAUDE.md o domínio não toca
   disco. O recurso `payloads/sqli.txt` é lido por um carregador em `app/` e a
   `List<Payload>` resultante é **injetada** no módulo pelo `Container`. O módulo fica
   puro quanto a disco e trivial de testar (recebe a lista pronta).

## Escopo

### `src/main/resources/payloads/sqli.txt`

Arquivo de texto no classpath (vai no jar pelo shade). Uma linha por payload, campos
separados por TAB: `id` ⇥ `value` ⇥ `description`. Linhas em branco e as iniciadas por
`#` são ignoradas. O `type` de todo payload do arquivo é `SQL_INJECTION`. Conteúdo:

- Payloads de **aspas/erro** (disparam a assinatura de erro do detector): `'`, `"`,
  `' OR '1'='1' -- -`, etc. Ids `sqli-error-*` / `sqli-quote-*`.
- Payloads **temporais** com id começando em `sqli-time-` (o detector mede o atraso):
  MySQL `SLEEP`, ex.: `1' AND SLEEP(5)-- -`. O 5 s casa com o limiar do detector e com o
  timeout de 30 s do `Config`.
- Payloads **booleanos** entram comentados/anotados com nota de que a comparação é
  follow-up (não são enviados como caso de veredito nesta issue — ver decisão 3).

### `app/` — carregador de payloads

- Um carregador (classe pequena em `app/`, ex.: `PayloadLoader`) com:
  - método **puro** que faz o parse de linhas → `List<Payload>` (testável sem disco);
  - método que lê o recurso do classpath e delega ao parse.
- O `Container` carrega `payloads/sqli.txt` uma vez e injeta a lista no
  `SqlInjectionModule`.

### `domain/module/SqlInjectionModule`

- `public final class SqlInjectionModule implements VulnModule`.
- Construtor `(HttpClient http, Detector detector, List<Payload> payloads)` — colaboradores
  por construtor (DI manual na raiz de composição); `null` em qualquer um →
  `NullPointerException`. A lista é copiada (imutável).
- `type()` → `VulnType.SQL_INJECTION` (não lança, ao contrário do `SmokeModule`).
- `scan(target)`:
  1. `target` nulo → `NullPointerException`.
  2. Faz o login no DVWA e monta o header de sessão (`Cookie`).
  3. Para cada `payload`: monta o `HttpRequest` do ponto de injeção com o payload no
     parâmetro `id` e o header de sessão; envia pelo `HttpClient`; monta
     `Evidence(payload, request, response)`; chama `detector.judge(evidence)`.
  4. Veredito com `vulnerable() == true` → acrescenta
     `Finding(SQL_INJECTION, Severity.CRITICAL, target, evidence, verdict)`; veredito
     negativo não vira `Finding`. Ordem dos findings = ordem dos payloads.
  5. Devolve a lista (vazia se nada foi confirmado).
- Erros de transporte do `HttpClient` propagam (coerente com `Scanner`/`SmokeModule`: um
  alvo fora do ar não pode produzir relatório "limpo").

### `app/Container`

- Registra `sqli` → `new SqlInjectionModule(httpClient, new HeuristicDetector(), payloads)`.
- Remove o `smoke` do registro de módulos (era temporário "até o primeiro módulo real",
  dito no Javadoc do `SmokeModule` e do `Container`). O `SmokeModule` e seu teste
  **permanecem** no código (ainda provam o circuito), só saem do mapa de `--module`.

## Fora do escopo

- SQLi **booleana** (comparação verdadeiro/falso) — follow-up; payload fica anotado.
- Linha de base (resposta sem payload) para descontar páginas já com erro/lentas —
  limitação conhecida da heurística, medida como falso positivo no experimento.
- Outros alvos além do DVWA e outros pontos de injeção — issues futuras.
- Detector LLM, severidade variável por payload, e qualquer regra nova no
  `HeuristicDetector` (já fechado em #9).

## Contratos tocados

Nenhum. `VulnModule`, `Detector`, `Evidence`, `Payload`, `Finding`, `HttpRequest/Response`
ficam como em `docs/contracts.md`. A convenção do prefixo `sqli-time-` já está no Javadoc
do `HeuristicDetector`; o formato do `payloads/sqli.txt` fica documentado no Javadoc do
carregador.

## Critérios de aceite

Testes de unidade, sem rede, com `HttpClient` fake (respostas roteirizadas) e `Detector`
fake (vereditos roteirizados):

**`SqlInjectionModuleTest`**
1. `type()` é `VulnType.SQL_INJECTION`.
2. Construtor com qualquer argumento nulo → `NullPointerException`; `scan(null)` → NPE.
3. O módulo faz o login antes de atacar: com um fake que exige `user_token`/cookie, as
   requisições de payload carregam o header `Cookie` com `PHPSESSID` e `security=low`.
4. Cada payload da lista gera exatamente uma requisição de ataque, na ordem, com o valor
   do payload no parâmetro `id` da URL do ponto de injeção.
5. O `Detector` é chamado uma vez por payload, com a `Evidence` daquele payload
   (`evidence.payload()` é o payload, `response` é a resposta recebida).
6. Veredito vulnerável vira `Finding` com `type == SQL_INJECTION`, `severity == CRITICAL`,
   `target` ecoado, `evidence` e `verdict` os daquele payload; veredito negativo não vira
   `Finding`.
7. Mistura de vereditos positivos e negativos → só os positivos viram `Finding`, na ordem
   dos payloads.
8. Lista de payloads vazia → nenhum ataque, nenhum `Finding` (login pode nem ocorrer).

**`PayloadLoaderTest`**
9. Parse puro: linhas em branco e `#comentário` ignoradas; `id⇥value⇥description` vira
   `Payload` com `type == SQL_INJECTION`; `value`/`id` preservados (inclusive espaços
   internos do value).
10. O recurso real `payloads/sqli.txt` carrega: lista não vazia, contém ao menos um id
    `sqli-time-*` (temporal) e ao menos um payload de aspas/erro; nenhum id repetido.

**Integração do `Container`**
11. `Container` registra `sqli` e **não** registra `smoke`; `sqli` resolve para um
    `SqlInjectionModule` com `type() == SQL_INJECTION` (ajuste do `ContainerTest`).

**Verificação final**
12. `DependencyRuleTest` verde (domínio sem referência a `adapter`/`app`/`cli`; o módulo
    não lê disco). `mvn clean verify` verde.

**Checkpoint da Fase 2 (manual, com laboratório; não é teste de unidade)**
13. `docker compose -f lab/docker-compose.yml up -d`, depois
    `java -jar target/vulnscan.jar scan --target http://localhost:8080 --module sqli`
    grava um `report.md` com ao menos um `Finding` de SQL_INJECTION no DVWA. A evidência
    (trecho do relatório) é anexada como comentário no PR / issue.
