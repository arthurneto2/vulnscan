# Spec — ApiAuthModule: autenticação e autorização em API REST (issue #14)

Referência: issue #14 (Fase 4 de `docs/build-order.md`): "`ApiAuthModule`: acesso sem token,
com token de outro usuário (IDOR) e papel indevido". Depende de #8 e #15 (regras de
auth/acesso, já feitas).

## Objetivo

Módulo que testa controle de acesso numa API REST: faz requisições que **não deveriam** ser
autorizadas, monta a `Evidence` e pede o veredito ao `Detector`. As regras de #15 decidem
(2xx com dados onde se esperava 401/403 → vulnerável). Fecha o checkpoint da Fase 4.

## Verificado no laboratório (2026-11-04, Juice Shop)

- Login: `POST /rest/user/login` (JSON `{email,password}`) → `{"authentication":{"token","bid"}}`;
  `bid` é o id da cesta do usuário. Registro: `POST /api/Users` (JSON) → 201.
- **Sem token (BROKEN_AUTH):** `GET /rest/admin/application-configuration` → 200 com config
  (positivo); `GET /api/Users` → 401 (negativo, protegido).
- **IDOR (BROKEN_ACCESS_CONTROL):** com o token do usuário, `GET /rest/basket/<outro_bid>`
  → 200 com a cesta alheia (positivo).
- **Papel indevido (BROKEN_ACCESS_CONTROL):** usuário comum em `GET /api/Users` → 200,
  lista todos os usuários (positivo); em `GET /rest/order-history/orders` → 403 (negativo).

## Escopo

### 1. Interface do módulo: `ApiSession` (`domain/module`)

Isola o login específico do alvo (parse de JSON) do domínio — como o `XssBrowser` isola o
Selenium. Não é porta.

```java
interface ApiSession {
    AuthSession authenticate(Target target);     // registra/loga e devolve token + id do recurso próprio
    record AuthSession(String token, String ownResourceId) {}   // campos não nulos
}
```

### 2. `AuthProbe` e `ApiAuthModule` (`domain/module`)

```java
enum AuthMode { NONE, USER_TOKEN, USER_TOKEN_OTHER_RESOURCE }
// NONE: sem Authorization. USER_TOKEN: Bearer do usuário. USER_TOKEN_OTHER_RESOURCE:
// Bearer do usuário e {otherId} no path trocado por um id != do próprio (IDOR).
record AuthProbe(VulnType type, String name, AuthMode mode, String pathTemplate) {}
```

- `ApiAuthModule(HttpClient http, Detector detector, ApiSession session, List<AuthProbe> probes)`.
  Nulos → NPE; `probes` vazio → `IllegalArgumentException`. `type()` → `BROKEN_AUTH`.
  (Um módulo com dois `VulnType`; `type()` devolve o "principal", `BROKEN_AUTH`, só para a
  interface — o `Finding` usa o tipo de cada probe. Documentado.)
- `scan(target)`: autentica **uma vez** e só se algum probe precisar de token (lazy). Para
  cada probe, na ordem:
  1. `GET` em `baseUrl.resolve(path)` (com `{otherId}` trocado, se for o caso) com os
     headers do alvo mais `Authorization: Bearer <token>` quando o modo pede;
  2. `Evidence(payload sintético do probe, request, response)` → `detector.judge(...)`;
  3. vulnerável → `Finding(probe.type(), HIGH, target, evidence, verdict)`. **Não** encerra
     nada: cada probe é um caso independente (diferente dos módulos de payload).
- `{otherId}`: `ownResourceId` vizinho — se inteiro e > 1, `n-1`; senão `n+1`.
- Payload sintético: `new Payload(probe.name(), probe.type(), path efetivo, descrição)`.
- Erros do `HttpClient` e da `ApiSession` propagam.

### 3. Adaptador `adapter/http/JuiceShopSession implements ApiSession`

- Registra um usuário novo (email único `vulnscan-<random>@test.local`) via `POST /api/Users`
  e loga via `POST /rest/user/login`, pela porta `HttpClient`; faz o parse do JSON com
  Jackson (adaptador pode). Devolve `AuthSession(token, String.valueOf(bid))`.
- Específico do Juice Shop (como o `SeleniumBrowser` é do Chrome). Falha de login (sem token
  no JSON) → `IllegalStateException`.

### 4. Composição

- `Container` registra `api` com `JuiceShopSession`, o `HeuristicDetector` e os probes:
  - `BROKEN_AUTH` NONE `/rest/admin/application-configuration` (positivo);
  - `BROKEN_AUTH` NONE `/api/Users` (negativo, 401);
  - `BROKEN_ACCESS_CONTROL` USER_TOKEN_OTHER_RESOURCE `/rest/basket/{otherId}` (IDOR positivo);
  - `BROKEN_ACCESS_CONTROL` USER_TOKEN `/api/Users` (papel positivo, 200 lista usuários);
  - `BROKEN_ACCESS_CONTROL` USER_TOKEN `/rest/order-history/orders` (papel negativo, 403).
- `api` roda sem navegador, mas faz login real (precisa do alvo no ar).

## Fora do escopo

- DVWA (o foco de auth/API é o Juice Shop). Refresh/expiração de token, CSRF, outros verbos.
- Distinguir dado sensível (é limitação da heurística; o experimento mede).

## Contratos tocados (`docs/contracts.md` e `docs/cli.md`)

- Novos: `ApiSession`/`AuthSession`, `AuthMode`, `AuthProbe`, `ApiAuthModule`. CLI: módulo `api`.

## Critérios de aceite

1. `ApiAuthModuleTest` (fakes de `HttpClient`, `Detector`, `ApiSession`):
   - NONE → `GET` sem `Authorization`; USER_TOKEN → com `Bearer <token>`;
     USER_TOKEN_OTHER_RESOURCE → `{otherId}` trocado por id != próprio e com `Bearer`;
   - autentica uma vez e só se algum probe pede token; sem probe com token não chama a sessão;
   - `Evidence` com a troca certa; cada probe vira um caso (positivo → `Finding` HIGH do tipo
     do probe; não encerra os outros);
   - erros de `HttpClient` e `ApiSession` propagam; validações do construtor; `type()`.
2. `JuiceShopSessionTest` com servidor em processo imitando `/api/Users` e `/rest/user/login`:
   devolve token e bid; login sem token no JSON → `IllegalStateException`.
3. `ContainerTest`: `api` nos módulos disponíveis (sem login no teste).
4. `mvn clean verify` verde, incluindo `DependencyRuleTest`.
5. Lab: `scan --module api --target http://localhost:3000` → achados de `BROKEN_AUTH`
   (admin-config) e `BROKEN_ACCESS_CONTROL` (IDOR e `/api/Users` com usuário comum); os
   negativos (`/api/Users` sem token → 401, `/rest/order-history/orders` → 403) não viram
   achado.
