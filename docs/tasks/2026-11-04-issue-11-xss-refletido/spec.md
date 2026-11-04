# Spec — XssModule: XSS refletido (issue #11)

Referência: issue #11 (Fase 3 de `docs/build-order.md`): "`XssModule` com marcador único;
refletido via `HttpClient`". Depende de #8. As regras de XSS do `HeuristicDetector` são da
#13 e o XSS armazenado (Selenium) é da #12.

## Objetivo

Módulo de XSS refletido no mesmo molde do `SqlInjectionModule`: injeta payloads com um
**marcador único** em pontos conhecidos, monta a `Evidence` de cada troca HTTP e pede o
veredito ao `Detector`. O marcador permite ao detector (#13) saber se o *nosso* payload
voltou cru na resposta, e não um conteúdo que já estava na página.

## Verificado no laboratório (2026-11-04, antes de implementar)

- DVWA `/vulnerabilities/xss_r/?name=` (logado; sem sessão → 302 para `login.php`):
  - `low`: reflete cru `<script>`, `<svg onload>` e `<img onerror>`;
  - `medium`: remove `<script>`; `<svg onload>` e `<img onerror>` passam crus;
  - `high`: idem ao `medium` (o `<script>` vira lixo);
  - `impossible`: exige `user_token` por requisição; sem ele não reflete nada.
- Juice Shop:
  - `/#/search?q=` é XSS **de DOM** (Angular no navegador): a busca vai para
    `/rest/products/search` (JSON) e o `q` não volta em nenhuma resposta HTTP. **Não é
    alcançável via `HttpClient`.**
  - `/redirect?to=<b>x</b>` → 406 com o valor **escapado** no HTML (`&lt;b&gt;`): caso
    negativo útil (o marcador aparece, mas escapado).
  - `/rest/track-order/<payload>` devolve JSON com o valor sem `<>`: não reflete HTML.

## Escopo

### 1. `InjectionPoint.url(URI baseUrl, String value)` (refactor)

A montagem da URL que hoje é privada no `SqlInjectionModule` vira método do record
`InjectionPoint` (mesma regra: `baseUrl.resolve(path)` + `?`/`&` + `parameter=<valor
URL-encoded UTF-8>`), usada pelos dois módulos. Comportamento do SQLi não muda.

### 2. Arquivo de payloads `src/main/resources/payloads/xss.txt`

Mesmo formato do `sqli.txt` (lido por `PayloadFile.parse`, tipo `XSS_REFLECTED`). Todo
valor contém o marcador `{marker}`. Na ordem:

- `xss-script` — `<script>alert('{marker}')</script>`
- `xss-svg-onload` — `<svg onload=alert('{marker}')>`
- `xss-img-onerror` — `<img src=x onerror=alert('{marker}')>`
- `xss-attr-break-svg` — `"><svg onload=alert('{marker}')>` (sai de atributo entre aspas)

### 3. `domain/module/XssModule`

- `XssModule(HttpClient http, Detector detector, List<Payload> payloads,
  List<InjectionPoint> points, Supplier<String> markers)`.
  - Nulos → NPE; listas vazias, payload que não seja `XSS_REFLECTED` ou sem `{marker}` →
    `IllegalArgumentException` (citando o id).
  - `markers`: fonte de marcadores únicos (`java.util.function.Supplier`, do JDK — não é
    porta nova). Injetado para os testes serem determinísticos.
- `type()` → `XSS_REFLECTED`.
- `scan(target)`: para cada ponto, para cada payload (na ordem):
  1. `marker = markers.get()`; payload concreto = mesmo id/tipo/descrição, com `{marker}`
     trocado pelo marcador (é ele que vai na `Evidence`, para o detector saber o que
     procurar);
  2. `GET` em `point.url(baseUrl, valor)` com os headers do alvo;
  3. `Evidence` → `detector.judge(...)`;
  4. vulnerável → `Finding(XSS_REFLECTED, MEDIUM, target, evidence, verdict)` e encerra o
     ponto (mesma política do SQLi).
- Erro do `HttpClient` propaga.

### 4. Composição

- `Container` registra `xss` (depois de `sqli`) com `HeuristicDetector`, os payloads de
  `xss.txt` e os pontos: DVWA `/vulnerabilities/xss_r/` com `name`; Juice Shop
  `/redirect` com `to`. Marcadores: `"vsx" + 8 hex` de um `UUID` aleatório (só letras e
  dígitos, para sobreviver a filtros).
- Até a #13, o `HeuristicDetector` responde "sem regras heurísticas para XSS_REFLECTED"
  (negativo): o módulo roda e grava as evidências, mas **não gera achado**. Isso é
  esperado e fica dito no PR.

## Fora do escopo

- Regras de detecção de XSS (#13) e XSS armazenado/Selenium (#12).
- **XSS de DOM do Juice Shop (`/#/search?q=`)**: exige navegador. **Decidido: vai para a
  #12** (que já traz o Selenium atrás de uma interface do módulo). A nota do caso
  `js-xss-search` no `ground_truth.yaml` é ajustada quando o benchmark for montado.
- POST/formulários, crawler, `user_token` do DVWA `impossible`.

## Contratos tocados (`docs/contracts.md` no mesmo passo)

- `InjectionPoint` ganha `URI url(URI baseUrl, String value)`.
- Novo: construtor de `XssModule`. Convenção `{marker}` no `xss.txt`.

## Critérios de aceite

1. `InjectionPointTest`: path simples (`?`), path com query fixa (`&`), valor
   URL-encoded; `SqlInjectionModuleTest` continua verde sem mudança.
2. `xss.txt` do classpath: ≥ 4 payloads `XSS_REFLECTED`, todos com `{marker}`.
3. `XssModuleTest` com `HttpClient`, `Detector` e marcadores fakes (sem rede):
   - envia `GET` com o payload (marcador substituído) codificado no parâmetro e os headers
     do alvo;
   - cada requisição usa um marcador novo; a `Evidence` traz o payload concreto;
   - nenhum positivo → lista vazia, tudo tentado; positivo → um `Finding` MEDIUM por
     ponto e para no ponto; positivo em um ponto não impede o outro;
   - erro do `HttpClient` propaga; validações do construtor (inclui payload sem
     `{marker}`).
4. `ContainerTest`: `--module xss` envia requisições a `/vulnerabilities/xss_r/` e
   `/redirect` com marcadores distintos; `sqli` continua funcionando.
5. `mvn clean verify` verde, incluindo `DependencyRuleTest`.
6. Lab: `scan --module xss` contra o DVWA (`--header "Cookie: ..."`) e contra o Juice
   Shop sai com 0 e com 0 achados (sem regras até a #13). Conferência à mão, com `curl`,
   de que o DVWA `low` devolve cru o payload com o marcador. A detecção de verdade é o
   checkpoint da Fase 3, junto com #12 e #13.
