# Spec — Negativo de XSS de DOM e codificação de navegador no DomXssModule (issue #77)

## Objetivo

1. Medir o **FP do LLM em XSS de DOM**, a lacuna registrada em `docs/experiment.md` ("Sem
   negativo de XSS de DOM no gabarito"), com um negativo genuíno: o payload chega ao DOM
   renderizado, mas não executa.
2. Corrigir a **codificação do payload no `DomXssModule`**, que hoje usa o `URLEncoder` e
   esconde um XSS de DOM real no DVWA (low). O par low/impossible do DVWA serve de controle:
   o low prova que o mecanismo funciona naquela página, e assim o TN do impossible vale pelo
   motivo certo (lição da #68).

Evidência na issue #77: com o `URLEncoder`, o `</script>` vira `<%2Fscript>`, o
`decodeURI` da página não decodifica `%2F`, e nada executa (FN). Com `/` sem codificar, o
`alert` dispara no low; no impossible, nenhuma das duas URLs executa.

## Escopo

1. **`InjectionPoint.browserUrl(URI baseUrl, String value)`**: igual ao `url`, mas codifica o
   valor como um navegador codifica o fragmento. Percent-encode (UTF-8) só de: controles
   (0x00–0x1F, 0x7F), espaço, `"`, `#`, `%`, `<`, `>`, `[`, `\`, `]`, `^`, `` ` ``, `{`, `|`,
   `}` e não-ASCII. Isso equivale ao conjunto de fragmento do WHATWG (espaço, `"`, `<`, `>`,
   `` ` ``) mais o que o `java.net.URI` recusa. `/`, `'`, `(`, `)`, `=`, `&`, `;`, `:` e `+`
   ficam como estão.
2. **`DomXssModule`** passa a abrir `point.browserUrl(...)`. `XssModule` e
   `SqlInjectionModule` continuam com `url(...)`, a codificação HTTP de formulário.
3. **Gabarito** (`experiments/ground_truth.yaml`), alvo `dvwa`:
   - `dvwa-xss-dom-low`: `XSS_REFLECTED`, `location: "/vulnerabilities/xss_d/#?default="`,
     `security: low`, `vulnerable: true`. Notas: `document.write` com `decodeURI` do
     fragmento.
   - `dvwa-xss-dom-impossible`: mesmo `location`, `security: impossible`,
     `vulnerable: false`. Notas: reflete o valor sem decodificar (`%3Cscript%3E…`), não
     executa.

   O fragmento (`#?default=`) faz o `LabCaseRunner` escolher o `DomXssModule`, regra que já
   existe. O servidor não vê o fragmento; a página lê `document.location.href`. Com isso, o
   XSS refletido passa a ter 3 positivos e 3 negativos.
4. Docs: `docs/contracts.md` (`InjectionPoint.browserUrl`), `docs/experiment.md` (a
   limitação "Sem negativo de XSS de DOM" vira o registro do que mudou, e as rodadas
   anteriores tinham 14 casos e o `URLEncoder`), `docs/ground-truth.md` (exemplo de DOM com
   fragmento, se couber).

### Fora do escopo

- Novas rodadas de referência com Ollama e API: são a próxima task (refazer as referências
  com `--reset` e as colunas `erro`/`erros`), já com os 16 casos.
- Mudar a codificação dos módulos HTTP.
- Outros casos de DOM (níveis medium/high do DVWA).

## Contratos tocados (`docs/contracts.md`)

- `InjectionPoint`: novo `public URI browserUrl(URI baseUrl, String value)`.
- `DomXssModule`: mesma assinatura; a URL aberta muda de codificação.

## Critérios de aceite (viram testes)

1. `InjectionPointTest#browserUrlCodificaComoONavegador`: ponto
   `("/vulnerabilities/xss_d/#", "default")`, valor `<script>alert('vsx1')</script>` →
   `http://localhost:8080/vulnerabilities/xss_d/#?default=%3Cscript%3Ealert('vsx1')%3C/script%3E`.
2. `InjectionPointTest#browserUrlCodificaEspacoAspasENaoAscii`: `<img src="x" é>` →
   `%3Cimg%20src=%22x%22%20%C3%A9%3E`; `%`, `#`, `{` → `%25`, `%23`, `%7B`.
3. `InjectionPointTest#urlContinuaComoFormulario`: o `url` atual não muda (teste existente
   ou um novo, com `+` e `%2F`).
4. `DomXssModuleTest#abreUrlComFragmentoEMarcador` (ajustado): a URL aberta é
   `…/#/search?q=%3Cimg%20src=x%20onerror=alert('m1')%3E`.
5. `GroundTruthFileTest#xssDeDomTemPositivoENegativo`: entre os casos com `#` no
   `location`, há `vulnerable: true` e `vulnerable: false`.
6. `mvn clean verify` verde.
7. **Checkpoint empírico** (lab real, `--reset`):
   1. o gabarito de rascunho da issue (só os 2 casos do DVWA), heurística: low **TP**,
      impossible **TN**;
   2. gabarito completo, `--runs 1 --reset --llm api`: heurística F1 1.000 nos 16 casos;
      `js-xss-dom-search` continua TP nos dois detectores; anotar o veredito do LLM no
      `dvwa-xss-dom-impossible` (primeira medida de FP de DOM) e `erros` = 0, ou refazer.

## Referências

- Issue #77; limitação em `docs/experiment.md`; janela do corpo da #72 (o LLM vê o trecho
  ao redor do marcador `vsx…`, que no impossible aparece codificado).
