# Plano — Benchmark cria o banco do DVWA antes do login (issue #59)

Spec: `spec.md` nesta pasta. Branch: `issue-59-dvwa-setup-banco`.

## Passos

- [x] **0. Branch e lab.** Branch criada a partir de `main`. O commit `ccbadb1`
  (`restart: "no"` no `lab/docker-compose.yml`) foi versionado à parte, a pedido.

- [x] **1. Login com banco pronto (red → green, extração).**
  Teste primeiro: `DvwaSessionTest#loginComBancoProntoNaoChamaSetup`. O fake de `HttpClient`
  é escrito à mão, responde por método + path e grava as requisições. `GET /login.php`
  devolve `set-cookie: PHPSESSID=abc` e o token; `POST /login.php` devolve
  `302 location: index.php`. O teste espera o cookie `PHPSESSID=abc; security=low` e nenhuma
  requisição a `/setup.php`.
  Red: `DvwaSession` ainda não existe, então o teste não compila. Antes, criar só o esqueleto
  (`login` devolvendo `""`) para o teste falhar na asserção.
  Green: mover o `login` e os padrões `USER_TOKEN`/`PHPSESSID` do `LabCaseRunner` para o
  `DvwaSession`.
  Verificar: `mvn test -Dtest=DvwaSessionTest`.

- [x] **2. Cria o banco quando o login cai em `setup.php` (red → green).**
  Teste: `DvwaSessionTest#criaBancoQuandoLoginRedirecionaParaSetup`. O primeiro
  `POST /login.php` dá `302 location: setup.php`, o segundo dá `302 location: index.php`;
  `GET /setup.php` traz o token `5e7`; o segundo `GET /login.php` traz `PHPSESSID=def`.
  O teste espera um `POST /setup.php` cujo corpo tem `create_db=` e `user_token=5e7`, e o
  cookie final `PHPSESSID=def; security=low`.
  Red: hoje não há chamada a `setup.php` (falha na asserção).
  Green: o fluxo do passo 2.3 da spec.
  Verificar: `mvn test -Dtest=DvwaSessionTest`.

- [x] **3. Falha alto se o setup não resolve (red → green).**
  Teste: `DvwaSessionTest#falhaSeSetupNaoCriaOBanco`. Os dois logins dão
  `302 location: setup.php`. O teste espera uma `IllegalStateException` cuja mensagem contém
  `setup.php`.
  Red: hoje devolve o cookie sem erro.
  Green: checar o segundo login e lançar a exceção.
  Verificar: `mvn test -Dtest=DvwaSessionTest`.

- [x] **4. `LabCaseRunner` usa o `DvwaSession` (refactor).** Trocar o `login` privado por
  `dvwaSession.login(...)` dentro do `computeIfAbsent` e remover os padrões duplicados. O
  comportamento é coberto pelos testes acima. Verificar: `mvn test`. (O campo se chama
  `dvwaSession` porque `dvwa` colidia com a variável local `boolean dvwa` de `target()`.)

- [x] **5. README.** Na seção do laboratório: abrir `http://localhost:8080/setup.php` →
  *Create / Reset Database* antes do login manual, e explicar que o `benchmark` faz isso
  sozinho. Em `docs/experiment.md`, onde diz "banco do DVWA criado antes de cada execução",
  anotar que, depois da #59, o próprio benchmark cria o banco.

- [x] **6. `mvn clean verify`.** 333 testes, 0 falhas, BUILD SUCCESS.

- [x] **7. Checkpoint empírico.** `docker compose -f lab/docker-compose.yml down && ... up -d`,
  **sem** abrir o `setup.php`, e depois
  `java -jar target/vulnscan.jar benchmark --ground-truth experiments/ground_truth.yaml --runs 1 --out <tmp>`.
  A heurística tem de repetir a linha de base (VP 8 / FP 0 / VN 6 / FN 0). Conferir nos logs
  do container `POST /setup.php` seguido de `POST /login.php` → `302 index.php`. O detector
  `llm` roda junto (Ollama ou `--llm api`); o número dele não é critério desta issue.
  - **Resultado (2026-11-16):** lab resetado sem abrir o `setup.php`; `benchmark --runs 1`
    terminou com código 0. Heurística VP 8 / FP 0 / VN 6 / FN 0, igual à linha de base. Nos
    logs do DVWA: `POST /login.php` 302 → `GET /setup.php` → `POST /setup.php` →
    `GET /login.php` → `POST /login.php` 302, e o SQLi `low` volta 200 (sessão válida).
    O Ollama estava desligado e a chave da API não estava no ambiente, então o `llm` deu
    0 VP (confiança 0 em tudo). O número do `llm` não é critério desta issue.
  - **Achado fora do escopo (já acontecia antes):** no nível `impossible`, as páginas
    `sqli` e `xss_r` do DVWA exigem `user_token` (anti-CSRF) e, sem ele, respondem
    `302 → index.php` antes de executar a consulta. O login nesse nível funciona (conferido
    com curl). Então `dvwa-sqli-impossible` e `dvwa-xss-reflected-impossible` dão VN sem que
    o scanner chegue a ver a página segura. Isso afeta a validade dos negativos do DVWA:
    precisa de issue própria.
