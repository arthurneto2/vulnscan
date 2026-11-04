# Spec — HeuristicDetector: regras de XSS (issue #13)

Referência: issue #13 (Fase 3 de `docs/build-order.md`): "Regras de XSS no
`HeuristicDetector`". Depende de #9. Feita antes da #12 (decisão de 2026-11-04) para a #11
já detectar no DVWA e a #12 nascer com checkpoint verificável.

## Objetivo

Trocar o ramo "sem regras" de `XSS_REFLECTED` e `XSS_STORED` por regras puras e
determinísticas. O `XssModule` (#11) manda na `Evidence` o payload **concreto**, com o
marcador único já substituído; a pergunta é se esse payload voltou na resposta de um jeito
que o navegador executaria.

## Verificado no laboratório (2026-11-04, na #11)

- DVWA `xss_r` `low`: devolve `<svg onload=alert('vsx…')>` cru, em `text/html`.
- DVWA `medium`/`high`: removem `<script>` (sobra `alert('vsx…')</script>` ou lixo), mas
  `<svg onload>` e `<img onerror>` voltam crus.
- Juice Shop `/redirect?to=`: devolve `&lt;svg onload=alert(&#39;vsx…&#39;)&gt;` (escapado).
- Juice Shop `/rest/track-order/…`: JSON (`application/json`), sem `<>`.

## Escopo — regras de `XSS_REFLECTED` e `XSS_STORED` (as mesmas; a primeira que casar decide)

`body` = corpo da resposta (nulo → `""`); `value` = `payload.value()`;
`contentType` = header `content-type` da resposta (o `JdkHttpClient` devolve nomes em
minúsculas; ausente = desconhecido, tratado como HTML).

1. **Refletido cru em HTML.** `body` contém `value` literalmente (diferenciando
   maiúsculas) e `contentType` é ausente ou contém `html` →
   `Verdict(true, 0.9, "payload refletido sem escape em resposta HTML", ...)`.
2. **Refletido cru fora de HTML.** `body` contém `value`, mas `contentType` não é HTML
   (ex.: `application/json`, `text/plain`) →
   `Verdict(false, 0.5, "payload refletido, mas a resposta é <contentType>, não HTML", ...)`.
   Confiança baixa de propósito: há casos de *sniffing*; é um caso em que se espera que o
   LLM discorde ou concorde com mais contexto.
3. **Refletido escapado.** `value` aparece no `body` depois de decodificar as entidades
   `&lt; &gt; &quot; &#39; &#039; &#x27; &amp;` (mas não no `body` cru) →
   `Verdict(false, 0.8, "payload refletido, mas com escape de HTML", ...)`.
4. **Não refletido** (nem cru nem escapado; inclui reflexão parcial, como o `<script>`
   removido pelo DVWA `medium`) →
   `Verdict(false, 0.6, "payload não refletido na resposta", ...)`.

Confianças como constantes nomeadas. O detector não conhece o formato do marcador: compara
o payload inteiro, que já é único por requisição.

### Tipos ainda sem regras

`BROKEN_AUTH` e `BROKEN_ACCESS_CONTROL` continuam com `Verdict(false, 0.0, "sem regras
heurísticas para <tipo>", ...)` (#15).

## Fora do escopo

- Contexto de injeção (dentro de atributo, de `<script>`, de comentário), CSP e
  `X-XSS-Protection`: limitações conhecidas que o experimento mede.
- Decodificação completa de HTML (só as entidades acima).
- Selenium/XSS armazenado e de DOM (#12).

## Contratos tocados

Nenhum. Só o Javadoc do `HeuristicDetector` (regras de XSS).

## Critérios de aceite (`HeuristicDetectorTest`, `Evidence` montada à mão, sem rede)

1. Payload cru em `text/html` → vulnerável, 0.9 (refletido e armazenado; parametrizado).
2. Payload cru sem `content-type` → vulnerável (desconhecido conta como HTML).
3. Payload cru em `application/json` → não vulnerável, 0.5, justificativa cita o tipo.
4. Payload escapado como no Juice Shop (`&lt;…&#39;…&gt;`) → não vulnerável, 0.8.
5. `<script>` removido como no DVWA `medium` → não vulnerável, 0.6.
6. Corpo nulo → não vulnerável, sem exceção.
7. Mesmo payload em maiúsculas no corpo (`<SVG ONLOAD=…>`) → não conta como cru (regra
   literal, documentada).
8. `BROKEN_AUTH`/`BROKEN_ACCESS_CONTROL` → 0.0, "sem regras" (o teste parametrizado atual
   passa a excluir os tipos de XSS).
9. Regras de SQLi inalteradas; `mvn clean verify` verde, incluindo `DependencyRuleTest`.
10. Lab: `scan --module xss` com o `HeuristicDetector`: DVWA `low` → 1 achado; DVWA
    `medium` → 1 achado (pelo `svg`/`img`); Juice Shop → 0 achados (escapado).
