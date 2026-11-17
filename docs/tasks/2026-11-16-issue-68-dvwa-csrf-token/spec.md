# Spec — Negativos do DVWA (nível `impossible`) chegam à página real (issue #68)

Issue: #68. Fase relacionada: experimento (`docs/build-order.md`, `docs/experiment.md`).
Achado no checkpoint da #59.

## Objetivo

No nível `impossible`, o DVWA exige o token anti-CSRF (`user_token`) em toda submissão e,
sem ele, responde `302 → index.php` antes de executar a consulta. O scanner não manda o
token. Por isso os três negativos do DVWA dão VN sem que o detector chegue a ver a página
segura: o que ele avalia é o redirect. Isso tira validade justamente da medida de falso
positivo, que é onde se espera que a IA ajude.

O benchmark deve mandar o token certo para que esses casos sejam julgados pela página que
o DVWA realmente devolve (consulta parametrizada, saída escapada).

## Comportamento do DVWA (conferido com curl em 2026-11-16, imagem fixada do lab)

| Página | Submissão | Sem token | Com token |
|---|---|---|---|
| `sqli` | GET `?id=…&Submit=Submit` | `302 → sqli/index.php` | `200`, sem erro de SQL |
| `xss_r` | GET `?name=…` | `302 → xss_r/index.php` | `200`, `Hello &lt;script&gt;…` |
| `xss_s` | POST `txtName, mtxMessage, btnSign` | `302 → xss_s/index.php` | (gravado; ver checkpoint) |

- O token **muda a cada requisição**, e um token já usado é recusado (`302`). É preciso um
  token novo antes de cada payload.
- O token vem em `<input type='hidden' name='user_token' value='…' />` na própria página
  (`GET` do path sem query). O padrão é o mesmo do login e do setup.
- No nível `low`, as páginas não têm o campo. A visualização do guestbook (aberta pelo
  navegador) não exige token.

## Escopo

1. **`DvwaCsrfHttpClient`** (novo, só do pacote `app/benchmark`): decorador da porta
   `HttpClient`. Não é porta nova: implementa a que já existe, para a aplicação mexer na
   requisição sem o domínio saber de DVWA. Para cada requisição que **submete algo** (GET
   com query, ou POST com corpo):
   1. `GET` do mesmo path, sem query, com os mesmos headers (sem `Content-Type`);
   2. extrai o `user_token`;
   3. se achou: no GET, acrescenta `user_token=<t>` à query; no POST, acrescenta
      `&user_token=<t>` ao corpo. Se não achou, manda a requisição original sem mudar nada.
   - Requisição sem query e sem corpo passa direto, sem GET extra.
   - A resposta devolvida é a da requisição original (status, corpo e `elapsedMillis` dela).
2. **`LabCaseRunner`**: casos do DVWA com `security: impossible` usam o módulo com o
   `HttpClient` decorado. Os outros (DVWA `low` e Juice Shop) continuam com o cliente puro,
   sem requisição a mais.
3. **`DvwaSession`**: expõe o extrator do `user_token` para o pacote, para não duplicar o
   regex.
4. **`docs/experiment.md`**: nota em "Limitações". As rodadas anteriores a esta issue
   (tabelas da #61 e da #65) julgaram os 3 negativos `impossible` do DVWA pelo redirect, não
   pela página. O FP do LLM nesses casos pode ter saído subestimado. Anotar também que o
   tempo médio dos casos `impossible` inclui o GET do token, o mesmo para os dois
   detectores.

## Fora do escopo

- ~~Refazer as rodadas do experimento com o LLM.~~ Entrou no escopo a pedido do usuário
  (2026-11-16), depois do checkpoint: rodadas com o Qwen 14B local e o Nemotron Ultra, 3
  execuções cada, registradas em `docs/experiment.md`. O Lightning ficou de fora, também a
  pedido. Ver passo 10 do plano.
- O `scan` manual com `--header` em nível `impossible` (só o benchmark muda).
- Reset entre execuções (#60).

## Contratos tocados

Nenhum. `DvwaCsrfHttpClient` implementa a porta `HttpClient` sem mudar sua assinatura. As
classes novas são só do pacote, fora de `docs/contracts.md`.

## Critérios de aceite

1. GET com query: o decorador busca a página (path sem query), e a requisição enviada leva
   `user_token=<t>` na query, com os parâmetros originais preservados.
2. POST: o corpo enviado é o original + `&user_token=<t>`. O GET do token não leva
   `Content-Type`.
3. Página sem token: a requisição sai sem mudança.
4. GET sem query: passa direto, sem GET extra.
5. A resposta devolvida é a da requisição original.
6. `LabCaseRunner` usa o decorador só para DVWA `impossible`.
7. `mvn clean verify` verde.
8. Checkpoint empírico: lab resetado, `benchmark --runs 1`. Nos logs do DVWA, as
   submissões dos casos `impossible` voltam `200`, não `302`. A heurística continua
   VP 8 / FP 0 / VN 6 / FN 0, agora com VN pelo motivo certo.
