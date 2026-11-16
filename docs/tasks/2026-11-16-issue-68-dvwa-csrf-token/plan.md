# Plano — Negativos do DVWA (nível `impossible`) chegam à página real (issue #68)

Spec: `spec.md` nesta pasta. Branch: `issue-68-dvwa-csrf-token` (a partir de `main` com a
#59 mergeada).

## Passos

- [x] **1. GET com query leva o token (red → green).**
  Teste primeiro: `DvwaCsrfHttpClientTest#getComQueryRecebeTokenDaPagina`. Fake de
  `HttpClient` escrito à mão: `GET /vulnerabilities/sqli/` (sem query) devolve a página com
  `user_token` `a1b2`; o resto devolve `200 "resultado"`. Mandar
  `GET /vulnerabilities/sqli/?Submit=Submit&id=1%27`. Espera: 1º envio é o GET do path sem
  query, com o mesmo `Cookie`; o 2º tem a query `Submit=Submit&id=1%27&user_token=a1b2`; a
  resposta devolvida é `"resultado"`.
  Red: criar o esqueleto que só repassa (falha na asserção da query).
  Green: o fluxo do item 1 da spec, só para GET.
  Verificar: `mvn test -Dtest=DvwaCsrfHttpClientTest`.

- [x] **2. POST leva o token no corpo (red → green).**
  Teste: `postRecebeTokenNoCorpo`. `POST /vulnerabilities/xss_s/` com corpo
  `txtName=v&mtxMessage=x&btnSign=Sign+Guestbook` e `Content-Type`. Espera: o GET do token
  sem `Content-Type` e sem corpo; o POST com o corpo + `&user_token=a1b2`.
  Verificar: `mvn test -Dtest=DvwaCsrfHttpClientTest`.

- [x] **3. Sem token na página → requisição original (red → green).**
  Teste: `paginaSemTokenMandaOriginal`. A página não tem `user_token`; a requisição enviada
  é igual à original.

- [x] **4. GET sem query passa direto (red → green).**
  Teste: `getSemQueryNaoBuscaToken`. Um envio só, igual ao original.

- [x] **5. Extrator do token compartilhado (refactor).** `DvwaSession.userToken(String)`
  (package-private, static) passa a ser usado pelo `DvwaSession` e pelo decorador. Testes
  verdes.

- [x] **6. `LabCaseRunner` escolhe o cliente (red → green).**
  Teste: `LabCaseRunnerTest#impossibleDoDvwaUsaClienteComToken` e
  `lowEJuiceShopUsamClientePuro`, sobre `static HttpClient httpFor(Case, HttpClient)`
  (package-private). Espera: DVWA (`/vulnerabilities…`) + `impossible` → instância de
  `DvwaCsrfHttpClient`; `low`, sem `security` ou Juice Shop → o mesmo cliente recebido.
  Green: `httpFor` e `moduleFor` recebendo o cliente escolhido.
  Verificar: `mvn test -Dtest=LabCaseRunnerTest`.

- [x] **7. `docs/experiment.md`.** Nota em "Limitações" (rodadas anteriores à #68 e tempo
  do GET do token), conforme a spec.

- [x] **8. `mvn clean verify`.** 339 testes, 0 falhas, BUILD SUCCESS.

- [x] **9. Checkpoint empírico.** Reset do lab, `benchmark --runs 1 --out <scratchpad>`.
  Nos logs do DVWA: submissões `impossible` de `sqli`, `xss_r` e `xss_s` com `200`, sem
  `302` em `/vulnerabilities`. Heurística VP 8 / FP 0 / VN 6 / FN 0. O LLM só se o
  Ollama ou a chave estiverem disponíveis; o número dele não é critério.
  - **Resultado (2026-11-16):** lab resetado; `benchmark --runs 1` terminou com código 0.
    Nenhum `302` em `/vulnerabilities` nos logs do DVWA (antes da #68 eram 26). Os GETs
    `impossible` de `sqli` (10) e `xss_r` (8) levam `user_token` e voltam `200`; os 13
    `POST /vulnerabilities/xss_s/` voltam `200`. O guestbook ficou com 8 mensagens
    escapadas (`&lt;script&gt;…`, dos casos `impossible`) e 4 cruas (dos casos `low`): o
    POST com token foi aceito, e o detector viu a defesa real. Heurística VP 8 / FP 0 /
    VN 6 / FN 0, agora com os 3 VN do DVWA pelo motivo certo. O `llm` não rodou de verdade
    (Ollama desligado, sem chave da API).
  - **Notas de execução:** no passo 2, o red saiu como `StringIndexOutOfBoundsException` (o
    esqueleto supunha `?` na URL), não como falha de asserção. É a falta do comportamento,
    não erro de setup. O passo 5 (extrator compartilhado) foi feito junto com o green dos
    passos 3 e 4.
