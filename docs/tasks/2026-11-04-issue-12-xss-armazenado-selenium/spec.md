# Spec — XSS armazenado e de DOM via Selenium (issue #12)

Referência: issue #12 (Fase 3 de `docs/build-order.md`): "XSS armazenado via Selenium,
isolado atrás de uma pequena interface própria do módulo". Por decisão de 2026-11-04 (#11),
inclui também o **XSS de DOM do Juice Shop** (`/#/search?q=`), que só aparece no navegador.
Depende de #11 (`XssModule`, `InjectionPoint`, marcador) e #13 (regras de XSS).

## Objetivo

Detectar XSS que só se vê executando a página num navegador: o de DOM (Juice Shop) e o
armazenado (guestbook do DVWA). O domínio continua sem depender de Selenium: fala com uma
interface pequena do próprio módulo, implementada por um adaptador.

## Verificado no laboratório (2026-11-04, Selenium 4.25 + Chrome headless 153)

- Juice Shop `/#/search?q=<img src=x onerror=alert('vsx…')>` → o navegador dispara
  `alert("vsx…")`. O XSS não aparece em nenhuma resposta HTTP (a busca é JSON).
- O DOM renderizado **normaliza** o HTML: `<img src=x onerror=alert('m')>` vira
  `<img src="x" onerror="alert('m')">`. A regra literal da #13 daria falso negativo; o sinal
  confiável é o **alert com o marcador**.
- DVWA `/vulnerabilities/xss_s/` (guestbook, `POST txtName, mtxMessage, btnSign`):
  - `low`: a mensagem é gravada crua; abrir a página no navegador (com o cookie de sessão)
    dispara `alert("vsx…")`;
  - `medium`/`high`: a mensagem é filtrada (o campo nome continua vulnerável, mas com
    `maxlength=10` no formulário — fora do escopo).
- Ambiente: o `chromedriver` 147 do PATH não serve para o Chrome 153; sem ele no PATH, o
  Selenium Manager baixa o driver certo. É questão do ambiente, não do projeto.

## Decisões (2026-11-04)

1. **Sinal do navegador → pseudo-header.** A resposta da `Evidence` é sintética: status
   200, corpo = DOM renderizado (`document.documentElement.outerHTML`),
   `content-type: text/html` e, se houve diálogos, `x-vulnscan-dialogs` com os textos dos
   `alert/confirm/prompt` juntados por `", "` (regra de header repetido de `contracts.md`).
   Nenhum record muda; a convenção fica em `docs/contracts.md` (como o prefixo `sqli-time-`)
   e o nome é constante `HttpResponse.DIALOGS_HEADER`. O LLM também verá o header.
2. **Escopo do armazenado:** DVWA (guestbook). Armazenado no Juice Shop exige login de
   admin, captcha ou JWT → issue nova.

## Escopo

### 1. `HeuristicDetector`: regra 0 de XSS

Antes das regras da #13 (refletido e armazenado): se `x-vulnscan-dialogs` tem algum texto
(separado por `", "`) não vazio contido em `payload.value()` (ou seja, o marcador) →
`Verdict(true, 0.95, "o navegador executou o payload: diálogo com o marcador", ...)`.
Diálogo sem o marcador não conta (segue para as regras da #13).

### 2. Interface do módulo (`domain/module`)

```java
// Página aberta num navegador real: DOM depois do JavaScript, diálogos disparados e tempo.
record RenderedPage(String html, List<String> dialogs, long elapsedMillis) {}  // dialogs copiado; nulo → vazio

// Interface pequena dos módulos de XSS via navegador (não é porta: só para o domínio não
// depender de Selenium). headers: só "Cookie" é aplicado (WebDriver não manda header arbitrário).
interface XssBrowser {
    RenderedPage open(URI url, Map<String, String> headers);
}
```

### 3. `DomXssModule` (`XSS_REFLECTED`, nome `xss-dom`)

- `DomXssModule(XssBrowser browser, Detector detector, List<Payload> payloads,
  List<InjectionPoint> points, Supplier<String> markers)`; mesmas validações do
  `XssModule` (inclui `{marker}`).
- Para cada ponto e payload: troca `{marker}`, `url = point.url(baseUrl, valor)` (funciona
  com fragmento: `/#/search` + `?q=` fica dentro do fragmento), `browser.open(url,
  target.headers())`, `Evidence(payload, GET url, resposta sintética)`, veredito; positivo
  → `Finding` `MEDIUM` e encerra o ponto. Erro do navegador propaga.

### 4. `StoredXssModule` (`XSS_STORED`, nome `xss-stored`)

- `record StoredXssPoint(String submitPath, Map<String, String> fixedFields,
  String payloadField, String viewPath)`: onde gravar (POST de formulário) e onde ver.
- `StoredXssModule(HttpClient http, XssBrowser browser, Detector detector,
  List<Payload> payloads, List<StoredXssPoint> points, Supplier<String> markers)`.
- Para cada ponto e payload: troca `{marker}`; `POST submitPath` com
  `application/x-www-form-urlencoded` (campos fixos + `payloadField=valor`, UTF-8) e os
  headers do alvo; depois `browser.open(viewPath, target.headers())`. `Evidence(payload,
  o POST, resposta sintética da página de visualização)`. Positivo → `Finding` `HIGH` e
  encerra o ponto. Erros propagam.
- Suja o alvo (grava no banco): o marcador único evita confundir entradas antigas; o reset
  do lab entre execuções do benchmark já está no `CLAUDE.md`.

### 5. Adaptador `adapter/browser/SeleniumBrowser implements XssBrowser`

- Chrome headless (`--headless=new`, `--no-sandbox`, `--disable-dev-shm-usage`), um driver
  novo por `open` (isolado e simples; ~1–2 s), fechado no `finally`.
- `Cookie` dos headers → cookies no domínio do alvo (abre a origem antes e adiciona);
  demais headers ignorados (documentado).
- Espera o carregamento e um tempo fixo de assentamento (1,5 s) para SPAs; coleta os
  diálogos aceitando um a um; devolve `outerHTML`, diálogos e tempo.
- Falha do WebDriver propaga (CLI → saída 2). Binário: o padrão do Selenium (Chrome no PATH).

### 6. Composição

- `Container` registra, depois de `xss`: `xss-dom` (Juice Shop `/#/search`, `q`) e
  `xss-stored` (DVWA: submit e view em `/vulnerabilities/xss_s/`, campos fixos
  `txtName=vulnscan`, `btnSign=Sign Guestbook`, payload em `mtxMessage`). Payloads de
  `xss.txt` lidos com o tipo de cada módulo. Mesmo gerador de marcador.
- Rodar sem `--module` agora abre o navegador (precisa de Chrome).

## Fora do escopo

- XSS armazenado no Juice Shop (issue nova), campo nome do guestbook (`maxlength`), headers
  além de `Cookie` no navegador, sessão de navegador compartilhada entre requisições.

## Contratos tocados (`docs/contracts.md` e `docs/cli.md` no mesmo passo)

- `HttpResponse.DIALOGS_HEADER` e a convenção da resposta sintética.
- Novos: `RenderedPage`, `XssBrowser`, `DomXssModule`, `StoredXssPoint`, `StoredXssModule`.
- CLI: módulos `xss-dom` e `xss-stored`.

## Critérios de aceite

1. `HeuristicDetectorTest`: diálogo com o marcador → vulnerável, 0.95 (refletido e
   armazenado); diálogo sem o marcador e header ausente → regras da #13 inalteradas.
2. `RenderedPageTest`: diálogos copiados e imutáveis; nulo → vazio.
3. `DomXssModuleTest` (fakes de navegador, detector e marcadores): URL com fragmento e
   marcador; headers do alvo ao navegador; resposta sintética (200, `text/html`, DOM,
   `x-vulnscan-dialogs` só com diálogos); marcador novo por abertura; positivo → `MEDIUM`
   e para no ponto; erro do navegador propaga; validações.
4. `StoredXssModuleTest` (fakes de `HttpClient`, navegador, detector, marcadores): POST
   form-urlencoded com campos fixos, payload codificado e headers do alvo; depois abre a
   página de visualização; `Evidence` com o POST e a página renderizada; positivo →
   `HIGH` e para no ponto; erros do HTTP e do navegador propagam; validações.
5. `SeleniumBrowserTest` com servidor em processo: devolve o DOM pós-JavaScript, captura o
   texto do `alert`, envia o `Cookie` como cookie. **Pulado (assumption) se o Chrome não
   puder ser iniciado** no ambiente.
6. `ContainerTest`: módulos disponíveis `sqli, xss, xss-dom, xss-stored` (sem abrir
   navegador no teste).
7. `mvn clean verify` verde, incluindo `DependencyRuleTest`.
8. Lab: `scan --module xss-dom` no Juice Shop → 1 achado (diálogo com marcador);
   `scan --module xss-stored` no DVWA `low` → 1 achado; DVWA `medium` → 0.
