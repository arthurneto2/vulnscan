# Spec — JdkHttpClient implementando a porta HttpClient (issue #3)

Referência: issue #3 (milestone "Fase 1 — HTTP ponta a ponta"), primeiro item da Fase 1
em `docs/build-order.md`.

## Objetivo

Ter o adaptador real da porta `HttpClient`: `adapter/http/JdkHttpClient`, usando
`java.net.http`, que transforma um `HttpRequest` do domínio numa requisição de verdade e
devolve um `HttpResponse` com status, headers, corpo e tempo decorrido. É a base de todos
os módulos de ataque.

## Escopo

- `JdkHttpClient implements HttpClient`, em `br.ufv.vulnscan.adapter.http`.
- Construtores: `JdkHttpClient()` (timeout padrão de 30 s) e `JdkHttpClient(Duration timeout)`.
  O timeout vale por requisição.
- **Requisição:** método arbitrário (`GET`, `POST`, `PUT`, `DELETE`…), URL, headers e
  corpo do `HttpRequest`. `body == null` → sem corpo; `headers == null` → nenhum header.
- **Resposta:**
  - `status`: código HTTP como veio. 4xx e 5xx **não** lançam exceção — um 500 com erro
    de SQL é justamente a evidência que o scanner procura.
  - `headers`: mapa imutável; nomes em **minúsculas** (HTTP é case-insensitive e o
    `Map<String,String>` simples não é); valores repetidos (ex.: dois `Set-Cookie`)
    **juntados por `", "`**, na ordem recebida.
  - `body`: texto decodificado pelo charset do `Content-Type` (UTF-8 se ausente).
  - `elapsedMillis`: do envio até o corpo inteiro recebido (`System.nanoTime`). Base da
    detecção de SQLi temporal.
- **Redirecionamento:** **não segue** (`Redirect.NEVER`). O scanner precisa ver o 302
  (ex.: API redirecionando para login em vez de responder 401/200).
- **Versão:** HTTP/1.1 fixo (os alvos do lab falam 1.1; evita tentativa de upgrade h2c).
- **Erros de transporte** (conexão recusada, timeout, I/O): lançam `UncheckedIOException`
  com a causa original. Interrupção: restaura a flag de interrupção e lança
  `UncheckedIOException`. O contrato da porta não declara exceção checada; decidir o que
  um módulo faz com a falha é assunto das issues dos módulos.

## Fora do escopo

- Cookies/sessão automáticos (`CookieManager`), autenticação, proxy, TLS customizado.
  O DVWA precisa de sessão, mas isso entra quando o módulo de SQLi pedir (issue #10).
- Retentativa, limite de taxa, pool configurável.
- Headers restritos do `java.net.http` (`Host`, `Content-Length`, `Connection`…): se o
  chamador passar, a exceção do JDK (`IllegalArgumentException`) sobe como está.
- Restrição a `localhost` (regra de segurança do projeto) — é validação de alvo, fica na
  CLI/`ScanService` (issues #6/#7), não no transporte.

## Contratos tocados

Assinaturas: nenhuma. `docs/contracts.md` ganha uma linha em "Decisões fixadas"
registrando: nomes de header da resposta em minúsculas, repetidos juntados por `", "`,
redirect não seguido.

## Critérios de aceite (viram testes)

Todos contra `com.sun.net.httpserver.HttpServer` em porta efêmera (`0`), em processo,
sem rede externa nem Docker.

1. Envia método, caminho+query, header e corpo do `HttpRequest` (servidor registra e o
   teste confere) — `POST` com corpo.
2. `GET` com `body == null` e `headers == null` funciona.
3. Devolve status e corpo (ex.: `201` e corpo UTF-8 com acento).
4. Status 500 é devolvido como `HttpResponse`, sem exceção.
5. Nomes de header da resposta em minúsculas (`X-Custom` → `x-custom`).
6. Header repetido juntado por vírgula (`Set-Cookie: a=1` + `Set-Cookie: b=2` → `"a=1, b=2"`).
7. Mapa de headers da resposta é imutável.
8. Não segue redirect: `302` com `Location` volta como `302`.
9. `elapsedMillis` ≥ atraso do servidor (handler dorme 200 ms).
10. Timeout (cliente 100 ms, servidor dorme 1 s) → `UncheckedIOException`.
11. Conexão recusada (porta fechada) → `UncheckedIOException`.
12. `DependencyRuleTest` continua verde; `mvn clean verify` verde.
