# Spec — Benchmark cria o banco do DVWA antes do login (issue #59)

Issue: #59. Fase relacionada: experimento (`docs/build-order.md`, `docs/experiment.md`).
Relacionada: #60 (reset entre execuções), que depende desta.

## Objetivo

O protocolo manda resetar o laboratório entre as execuções do benchmark
(`docs/experiment.md`: "Alvo resetado entre execuções"). Num DVWA recém-subido, o banco
ainda não existe: o `POST /login.php` responde `302 Location: setup.php` e a sessão não fica
autenticada. Hoje o `LabCaseRunner` ignora a resposta do login e segue com um cookie
inválido. Os casos do DVWA então rodam contra o redirect para `/login.php`, não contra a página
vulnerável, e o resultado do experimento fica errado sem nenhum aviso.

O benchmark deve criar o banco sozinho quando o DVWA pedir, e falhar alto se não conseguir.

## Escopo

1. Extrair o login do DVWA do `LabCaseRunner` para uma classe nova, só do pacote,
   `app/benchmark/DvwaSession`, que recebe o `HttpClient` e devolve o header `Cookie`. A
   classe não é porta nem interface: é só para poder testar o login com um `HttpClient` fake.
2. Fluxo do `DvwaSession.login(baseUrl, level)`:
   1. `GET /login.php` → `PHPSESSID` (do `set-cookie`) e `user_token` (do corpo).
   2. `POST /login.php` com `admin`/`password` e o token.
   3. Se a resposta for um redirect cujo `location` contém `setup.php`:
      - `GET /setup.php` com o cookie → `user_token`;
      - `POST /setup.php` com `create_db=Create+%2F+Reset+Database&user_token=<token>`;
      - refazer os passos 1 e 2 com sessão nova.
   4. Se o segundo login ainda redirecionar para `setup.php`, lançar
      `IllegalStateException` com uma mensagem que diga que o banco do DVWA não foi criado.
      A CLI já traduz isso em código de saída `2`.
3. `LabCaseRunner` passa a usar o `DvwaSession` (o cache de cookie por nível continua).
4. README, seção do laboratório: criar o banco em `http://localhost:8080/setup.php`
   (*Create / Reset Database*) antes do login manual, e avisar que o `benchmark` faz isso
   sozinho.

## Fora do escopo

- Resetar o lab entre execuções do `--runs N` (#60).
- Detectar outros tipos de falha de login (senha errada, DVWA fora do ar). Se o login falhar
  por outro motivo, o comportamento continua o de hoje.
- O `scan` (que recebe o cookie por `--header`) não muda.
- O resto do README que a #63 cobre (digest do modelo, portas).

## Contratos tocados

Nenhum. `LabCaseRunner` mantém o construtor, e `DvwaSession` é só do pacote, fora de
`docs/contracts.md`.

## Critérios de aceite

1. Com o banco pronto (login → `302 index.php`), o login não chama `setup.php` e devolve
   `PHPSESSID=<sid>; security=<nível>`. Mesmo comportamento de hoje.
2. Com o login redirecionando para `setup.php`, há um `POST /setup.php` com `create_db` e o
   `user_token` da página de setup, depois um segundo login. O cookie devolvido usa a sessão
   do segundo login.
3. Se o segundo login ainda cair em `setup.php`, sai `IllegalStateException` com uma mensagem
   que cita o `setup.php`.
4. `mvn clean verify` verde.
5. Checkpoint empírico: com o lab recém-resetado e **sem** criar o banco à mão,
   `benchmark --runs 1` dá na heurística o mesmo resultado da linha de base
   (VP 8 / FP 0 / VN 6 / FN 0).
