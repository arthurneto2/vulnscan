# Spec — HeuristicDetector: regras de autenticação/autorização (issue #15)

Referência: issue #15 (Fase 4 de `docs/build-order.md`): "Regras de auth/acesso no
`HeuristicDetector` (ex.: 200 + dado sensível onde se esperava 401/403)". Depende de #9.
Feita antes da #14 (mesmo padrão de XSS: #13 antes da #12) para o `ApiAuthModule` nascer com
checkpoint verificável.

## Objetivo

Trocar o ramo "sem regras" de `BROKEN_AUTH` e `BROKEN_ACCESS_CONTROL` por regras puras e
determinísticas. O `ApiAuthModule` (#14) monta uma requisição que **não deveria** ser
autorizada (sem token, ou com token de outro usuário/papel) e põe a troca na `Evidence`; a
pergunta do detector é: o alvo deixou passar?

## Verificado no laboratório (2026-11-04)

- Juice Shop:
  - `GET /rest/admin/application-configuration` sem token → **200** com JSON de config
    (BROKEN_AUTH: endpoint sensível servido sem autenticação).
  - `GET /rest/basket/2` com o token do usuário A (cesta do usuário B) → **200** com os
    dados da cesta de B (BROKEN_ACCESS_CONTROL / IDOR).
  - `GET /rest/basket/1` sem token → **401** (`UnauthorizedError`): protegido (negativo).
  - `GET /rest/user/whoami` sem token → **200** `{"user":{}}`: 2xx de corpo vazio, não é
    vazamento (vira o caso de corpo trivial abaixo).
- DVWA: página protegida sem sessão → **302** com `Location: ../../login.php` (negativo).
  O `JdkHttpClient` não segue redirecionamento (o 3xx volta como resposta).

## Escopo — regras de `BROKEN_AUTH` e `BROKEN_ACCESS_CONTROL` (as mesmas; a primeira que casar decide)

Sobre a resposta da `Evidence` (`status`, corpo nulo → `""`, headers com nomes em
minúsculas). A requisição já foi montada pelo módulo para ser indevida; o detector só olha
o resultado.

1. **Bloqueado como esperado.** `status` é 401 ou 403 →
   `Verdict(false, 0.85, "acesso negado como esperado (status <N>)", ...)`.
2. **Redirecionado para login.** `status` é 3xx e o header `location` contém `login` →
   `Verdict(false, 0.7, "redirecionado para a tela de login (status <N>)", ...)`.
3. **Acesso concedido.** `status` é 2xx:
   - corpo, depois de `strip()`, é vazio, `{}` ou `[]` →
     `Verdict(false, 0.5, "resposta <N> com corpo vazio: sem dado exposto", ...)`
     (ex.: `whoami` sem token);
   - senão → `Verdict(true, 0.7, "acesso concedido (status <N>) onde se esperava 401/403",
     ...)`.
4. **Inconclusivo** (404, 5xx, outros) →
   `Verdict(false, 0.6, "status <N> inconclusivo para autenticação/autorização", ...)`.

Confianças (0.85 / 0.7 / 0.5 / 0.6) como constantes nomeadas. A regra não distingue dado
"sensível" de dado qualquer (só corpo vazio × não vazio): separar sensível é limitação
conhecida da heurística, e é onde se espera que o LLM ajude — o experimento mede.

### O detector não precisa de nova convenção

`BROKEN_AUTH` e `BROKEN_ACCESS_CONTROL` já vêm no `VulnType` do payload; que a requisição
era indevida (sem token / token de outro usuário) é responsabilidade do módulo (#14), por
construção. Nenhum contrato muda.

## Fora do escopo

- Distinguir conteúdo sensível de conteúdo trivial além de "corpo vazio".
- Diferença entre não autenticado (401) e autenticado sem permissão (403): as duas regras
  são iguais aqui; o que separa `BROKEN_AUTH` de `BROKEN_ACCESS_CONTROL` é o `VulnType` que o
  módulo põe.
- O `ApiAuthModule`, os endpoints e o fluxo de login/token (#14).

## Contratos tocados

Nenhum. Só o Javadoc do `HeuristicDetector` (regras de auth/acesso).

## Critérios de aceite (`HeuristicDetectorTest`, `Evidence` montada à mão, sem rede)

1. 200 com corpo de dados → vulnerável, 0.7 (BROKEN_AUTH e BROKEN_ACCESS_CONTROL;
   parametrizado). Justificativa cita o status.
2. 401 → não vulnerável, 0.85; 403 → idem (parametrizado).
3. 302 com `location` contendo `login.php` → não vulnerável, 0.7.
4. 302 sem login no `location` → inconclusivo (regra 4), não vulnerável.
5. 200 com corpo `{}`, `[]` ou em branco → não vulnerável, 0.5.
6. 404 e 500 → não vulnerável, 0.6 (regra 4).
7. Corpo nulo num 200 → não vulnerável (tratado como vazio), sem exceção.
8. Todo veredito com `detector() == "heuristic"`; regras de SQLi e XSS inalteradas.
9. `mvn clean verify` verde, incluindo `DependencyRuleTest`.
