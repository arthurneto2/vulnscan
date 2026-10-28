# Spec — HeuristicDetector: regras de SQL injection (issue #9)

Referência: issue #9 (Fase 2 de `docs/build-order.md`): "`domain/detection/HeuristicDetector`:
regras/regex por `VulnType`" e "Teste de unidade do `HeuristicDetector` com `Evidence` fixa
(sem rede)". Só as regras de SQLi; XSS e auth entram nas #13 e #15.

## Objetivo

Ter a implementação heurística de `Detector`: pura, determinística e sem I/O. Ela julga
uma `Evidence` de SQL injection e devolve um `Verdict` com confiança e justificativa. É a
linha de base que o experimento compara com o `LlmDetector`.

## Escopo

### `domain/detection/HeuristicDetector`

- `public final class HeuristicDetector implements Detector`, sem estado, construtor vazio.
- `name()` → `"heuristic"`.
- `judge(evidence)` despacha por `evidence.payload().type()`. Todo `Verdict` sai com
  `detector = "heuristic"`. `evidence` nulo → `NullPointerException`; corpo da resposta
  nulo é tratado como `""`.

### Regras de `SQL_INJECTION` (na ordem; a primeira que casar decide)

1. **Erro de banco no corpo da resposta.** Assinaturas de mensagens de erro de SGBD
   (regex, sem diferenciar maiúsculas):
   - MySQL/MariaDB (DVWA): `You have an error in your SQL syntax`,
     `Warning: mysql_`, `mysqli_`, `MariaDB server version`.
   - SQLite (Juice Shop): `SQLITE_ERROR`, `sqlite3.OperationalError`,
     `unrecognized token`.
   - PostgreSQL: `syntax error at or near`, `PSQLException`.
   - Genéricas: `SQLSTATE[`, `ODBC .* Driver`.
   - Casou → `Verdict(true, 0.9, "assinatura de erro de SQL na resposta: <SGBD>", ...)`.
2. **Atraso temporal.** Só para payload temporal: `payload.id()` começa com
   `sqli-time-` (convenção que a #10 segue no arquivo de payloads). Se
   `response.elapsedMillis() >= 5000` → `Verdict(true, 0.8, "resposta levou N ms com
   payload temporal (limiar 5000 ms)", ...)`. O limiar é constante do detector; cabe no
   timeout HTTP de 30 s do `Config`.
3. **Nada casou** → `Verdict(false, 0.6, "nenhuma assinatura de erro de SQL nem atraso
   temporal", ...)`.

Confianças fixas (0.9 / 0.8 / 0.6): a heurística é determinística, e o número só serve
para o relatório e para o CSV do experimento. Ficam como constantes nomeadas.

### Tipos ainda sem regras

`XSS_REFLECTED`, `XSS_STORED`, `BROKEN_AUTH` e `BROKEN_ACCESS_CONTROL` →
`Verdict(false, 0.0, "sem regras heurísticas para <tipo>", "heuristic")`. O detector
nunca lança exceção por tipo; as #13 e #15 trocam esse ramo pelas regras de verdade.

## Fora do escopo

- **SQLi booleana** (comparar a resposta de "condição verdadeira" com a de "condição
  falsa"). Uma `Evidence` carrega uma troca só, então o detector não tem como comparar.
  Se a #10 precisar dela, a comparação fica no módulo (ou o contrato muda lá, com
  `docs/contracts.md`). Aqui não se mexe em contrato.
- Linha de base (resposta sem payload) para descontar páginas que já mostram erro ou que
  já são lentas. Isso é limitação conhecida da heurística, que o experimento mede como
  falso positivo.
- Payloads e o arquivo de payloads (#10). Severidade do `Finding` (é do módulo).

## Contratos tocados

Nenhum. `Detector`, `Evidence` e `Verdict` ficam como estão em `docs/contracts.md`. A
convenção de prefixo `sqli-time-` fica documentada no Javadoc do `HeuristicDetector`.

## Critérios de aceite (`HeuristicDetectorTest`, `Evidence` montada à mão, sem rede)

1. `name()` é `"heuristic"`.
2. Corpo com erro de MySQL → vulnerável, 0.9, justificativa cita MySQL.
3. Idem para SQLite, PostgreSQL e o genérico `SQLSTATE[` (teste parametrizado).
4. A assinatura casa sem diferenciar maiúsculas.
5. Corpo limpo, payload não temporal, resposta lenta (8000 ms) → **não** vulnerável
   (o atraso só conta para payload temporal).
6. Payload `sqli-time-*` com 5000 ms → vulnerável, 0.8; com 4999 ms → não vulnerável.
7. Corpo limpo e rápido → não vulnerável, 0.6.
8. Corpo `null` → não vulnerável, sem exceção.
9. Payload de XSS/auth com corpo contendo erro de SQL → não vulnerável, 0.0 (sem regras
   para o tipo).
10. Todo veredito tem `detector() == "heuristic"`.
11. `judge(null)` → `NullPointerException`.
12. `DependencyRuleTest` verde (só JDK: `java.util.regex`); `mvn clean verify` verde.
