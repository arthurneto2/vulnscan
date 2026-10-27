# Spec — Teste da regra de dependência (issue #2)

Referência: issue #2 (milestone "Fase 1 — HTTP ponta a ponta"), antecipando o item de
revisão da regra de dependência da Fase 7 em `docs/build-order.md`. Regras em
`docs/architecture.md` → "Camadas e regra de dependência".

## Objetivo

Ter um teste JUnit que quebra o build (`mvn clean verify`) quando alguém viola a
regra de dependência da arquitetura hexagonal. Antecipar isso para a Fase 1 garante que
os adaptadores que começam a ser escritos agora (`JdkHttpClient`, `MarkdownReportWriter`)
nunca vazem para dentro do domínio.

## Escopo

Regras verificadas, olhando só referências a pacotes do próprio projeto
(`br.ufv.vulnscan.*`):

| Pacote de origem | Proibido referenciar                                         |
| ---------------- | ------------------------------------------------------------ |
| `domain/`        | `br.ufv.vulnscan.adapter`, `br.ufv.vulnscan.app`, `br.ufv.vulnscan.cli` |
| `port/`          | qualquer `br.ufv.vulnscan.*` que não seja `br.ufv.vulnscan.domain.model` (nem o próprio `port`) |

- Detecta `import`, `import static` e nomes totalmente qualificados no corpo do código
  (ex.: `br.ufv.vulnscan.adapter.http.JdkHttpClient x;`), inclusive em Javadoc
  (`{@link ...}`) — um link para o adaptador também é acoplamento.
- Imports curinga contam: `import br.ufv.vulnscan.domain.*;` em `port/` é violação.
- Imports da JDK (`java.*`, `javax.*`) não são verificados.
- A falha lista cada violação no formato `<arquivo>:<linha> -> <pacote referenciado>`.

## Fora do escopo

- Proibir bibliotecas externas (Jackson, Selenium, picocli, jsoup) no domínio. A tabela
  da arquitetura sugere isso, mas a issue não pede; fica como possível issue futura.
- Regras de `adapter/`, `app/` e `cli/` (ex.: `cli` só importar `app`).
- Análise de bytecode ou dependência nova (ArchUnit etc.). Só JUnit 5 + JDK.

## Contratos tocados

Nenhum. Nada em `src/main` muda; `docs/contracts.md` não é alterado.

## Desenho

- `src/test/java/br/ufv/vulnscan/architecture/DependencyRuleChecker.java`: classe de
  teste (não vai para o jar) com `List<String> violations(Path sourceRoot)` que varre os
  `.java` sob `sourceRoot/br/ufv/vulnscan/{domain,port}` e devolve as violações.
- `src/test/java/br/ufv/vulnscan/architecture/DependencyRuleTest.java`: testes do
  verificador contra fontes falsos escritos em `@TempDir`, mais o teste que roda contra
  `src/main/java` de verdade.

## Critérios de aceite (viram testes)

1. Fonte em `domain/` com `import br.ufv.vulnscan.adapter...` → 1 violação apontando
   arquivo e linha.
2. Idem para `app` e `cli` importados em `domain/`.
3. Fonte em `port/` importando `br.ufv.vulnscan.domain.detection...` → violação.
4. Fonte em `port/` importando `br.ufv.vulnscan.domain.model...` e `java.nio.file.Path`
   → nenhuma violação.
5. Nome totalmente qualificado de `adapter` no corpo de uma classe de `domain/` →
   violação.
6. `import static br.ufv.vulnscan.app...` em `domain/` → violação.
7. O código atual em `src/main/java` → nenhuma violação.
8. `mvn clean verify` verde.
