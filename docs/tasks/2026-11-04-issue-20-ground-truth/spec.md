# Spec — Expandir ground_truth.yaml com casos positivos e negativos (issue #20)

Referência: issue #20 (Fase 6 de `docs/build-order.md`) e `docs/ground-truth.md`. Depende de
#10, #13, #15 (módulos e regras dos três tipos). O `BenchmarkRunner` (#21) consome o arquivo.

## Objetivo

Trocar o gabarito de exemplo por um conjunto real, validado no laboratório: positivos E
negativos dos três tipos de falha (SQLi, XSS, auth/API), ~metade negativos por tipo. Cada
caso tem que ser alcançável pelo módulo correspondente.

## Esquema (pequena adição)

Mantém `docs/ground-truth.md`; acrescenta um campo **opcional** `security` por caso, para o
DVWA, porque o mesmo `location` muda de vulnerável para não vulnerável conforme o nível
(`low` vs `impossible`). Documentado no `ground-truth.md`. Demais campos inalterados
(`id`, `type`, `location`, `vulnerable`, `notes`, `payload_ref` opcional).

## Casos (validados no lab em 2026-11-04)

DVWA (`http://localhost:8080`):
- `dvwa-sqli-low` SQL_INJECTION `/vulnerabilities/sqli/?Submit=Submit&id=` security=low → **true**
  (erro de MySQL).
- `dvwa-sqli-impossible` SQL_INJECTION mesmo local, security=impossible → **false** (prepared).
- `dvwa-xss-reflected-low` XSS_REFLECTED `/vulnerabilities/xss_r/?name=` low → **true** (reflete cru).
- `dvwa-xss-reflected-impossible` XSS_REFLECTED mesmo local, impossible → **false** (escapa).
- `dvwa-xss-stored-low` XSS_STORED `/vulnerabilities/xss_s/` low → **true** (grava cru; alerta).
- `dvwa-xss-stored-impossible` XSS_STORED mesmo local, impossible → **false** (sanitiza).

Juice Shop (`http://localhost:3000`):
- `js-sqli-search` SQL_INJECTION `/rest/products/search?q=` → **true** (SQLITE_ERROR).
- `js-xss-dom-search` XSS_REFLECTED `/#/search?q=` → **true** (XSS de DOM; módulo `xss-dom`).
- `js-xss-redirect-escaped` XSS_REFLECTED `/redirect?to=` → **false** (reflete escapado).
- `js-auth-admin-config` BROKEN_AUTH `/rest/admin/application-configuration` sem token → **true** (200 com config).
- `js-auth-users-protected` BROKEN_AUTH `/api/Users` sem token → **false** (401).
- `js-idor-basket` BROKEN_ACCESS_CONTROL `/rest/basket/{otherId}` com token → **true** (IDOR).
- `js-bac-users-role` BROKEN_ACCESS_CONTROL `/api/Users` com token de usuário comum → **true** (lista todos).
- `js-bac-order-history` BROKEN_ACCESS_CONTROL `/rest/order-history/orders` com token comum → **false** (403).

Balanço: SQLi 2+/1−, XSS_REFLECTED 2+/2−, XSS_STORED 1+/1−, BROKEN_AUTH 1+/1−,
BROKEN_ACCESS_CONTROL 2+/1−. Positivos 8, negativos 6; cada tipo com ≥1 de cada.

## Fora do escopo

- `BenchmarkRunner` e o mapeamento caso→módulo (#21). Subir os alvos (já validado à mão aqui).
- Casos de SQLi negativo no Juice Shop (o ponto de busca é injetável; negativo de SQLi fica no
  DVWA impossible). Documentado.

## Contratos tocados

Nenhum de código. `docs/ground-truth.md` ganha o campo opcional `security`.

## Critérios de aceite

1. `GroundTruthFileTest` (lê `experiments/ground_truth.yaml` com Jackson YAML, offline):
   - parseia; todo caso tem `id` (único), `type` (um `VulnType` válido), `location` não vazio,
     `vulnerable` booleano;
   - os três tipos de falha presentes: `SQL_INJECTION`, algum `XSS_*`, algum `BROKEN_*`;
   - cada `type` presente tem ≥1 positivo e ≥1 negativo.
2. Os casos acima estão no arquivo, com `base_url` por alvo.
3. `mvn clean verify` verde, incluindo `DependencyRuleTest`.
4. Validação de lab registrada nesta spec (não roda no `verify`).
