# Spec — domain/Scanner (issue #4)

Referência: issue #4 (milestone "Fase 1 — HTTP ponta a ponta"), item "`domain/Scanner`
que roda uma lista de `VulnModule` e junta os `Finding`" da Fase 1 em
`docs/build-order.md`. Fluxo em `docs/architecture.md`.

## Objetivo

Ter o orquestrador do domínio: dado um `Target`, roda cada `VulnModule` recebido e junta
os `Finding` num `ScanReport` com instante de início e de fim. É o ponto que o
`ScanService` (issue #6) chama.

## Escopo

Contrato já fixado em `docs/contracts.md` (sem mudança):

```java
public final class Scanner {
    public Scanner(List<VulnModule> modules) { ... }
    public ScanReport scan(Target target) { ... }
}
```

Comportamento:

- Roda os módulos **na ordem da lista**, um por vez, e concatena os `Finding` nessa ordem.
- Cada módulo recebe o mesmo `Target` passado a `scan`.
- `startedAt` é tomado antes do primeiro módulo; `finishedAt`, depois do último
  (`Instant.now()`).
- Lista vazia de módulos → `ScanReport` com `findings` vazio (não é erro).
- `ScanReport.findings` é imutável.
- A lista de módulos é copiada no construtor (`List.copyOf`): mudar a lista original
  depois não afeta o `Scanner`.
- `modules` nulo ou `target` nulo → `NullPointerException` (`Objects.requireNonNull`).
- **Exceção de um módulo propaga** e interrompe o scan (sem relatório parcial). Motivo:
  o `ScanReport` não tem campo de erro, e engolir a falha geraria um relatório "limpo"
  falso — o pior resultado para um scanner. Falhas de uma requisição isolada são tratadas
  dentro do módulo (issues dos módulos); a CLI converte a exceção em código de saída `2`
  (issue #7).

## Fora do escopo

- Escolher quais módulos rodar (`--module`): quem monta a lista é `Container`/`ScanService`
  (issue #6). O `Scanner` roda o que recebe.
- Execução paralela, relatório parcial, captura de erro por módulo.
- `Clock` injetável: os testes verificam o tempo por limites (`antes ≤ startedAt ≤
  finishedAt ≤ depois`), sem mudar o contrato.

## Contratos tocados

Nenhum (implementa a assinatura já fixada).

## Critérios de aceite (viram testes, com `VulnModule` fake escrito à mão)

1. Sem módulos → relatório com o mesmo `Target` e `findings` vazio.
2. Findings de dois módulos juntados na ordem dos módulos.
3. Cada módulo recebe o `Target` do scan.
4. `startedAt ≤` instante em que o módulo roda `≤ finishedAt`, e ambos dentro do
   intervalo medido pelo teste.
5. `findings` do relatório é imutável.
6. Alterar a lista passada ao construtor depois não muda os módulos executados.
7. Exceção lançada por um módulo propaga para quem chamou `scan`; módulos seguintes não
   rodam.
8. `new Scanner(null)` e `scan(null)` lançam `NullPointerException`.
9. `DependencyRuleTest` verde; `mvn clean verify` verde.
