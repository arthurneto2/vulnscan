# Plano — regra de dependência: domain sem libs externas (issue #29)

Spec: `spec.md` nesta pasta. Branch: `issue-29-dependency-rule-libs`.

O `DependencyRuleChecker` hoje recebe um `sourceRoot` e varre os fontes reais. Para testar
"o checker barra X" sem sujar o código real, adiciono um método que avalia **uma linha de
import** (`forbiddenImport(camada, linha)` ou similar) e testo-o com linhas sintéticas; o
teste de integração (`violations` sobre `src/main/java`) continua exigindo lista vazia.

## Passos

- [x] **1. Regra de import externo/JDK (unidade).** Teste novo
  `DependencyImportRuleTest`: linhas de import que DEVEM ser barradas no domínio
  (`org.openqa.selenium.*`, `picocli.*`, `org.jsoup.*`, `java.net.http.*`, `java.nio.file.*`)
  e que NÃO devem (`java.net.URI`, `java.util.regex.Pattern`, `com.fasterxml.jackson.*`,
  `br.ufv.vulnscan.*`). Red → implementar a checagem de import no `DependencyRuleChecker`.

- [x] **2. Ligar ao domínio.** A varredura de `domain/` passa a aplicar também a regra de
  import; `DependencyRuleTest` (sobre `src/main/java`) continua verde (lista vazia).

- [x] **3. Doc.** Nota em `docs/architecture.md` (libs externas e rede/disco do JDK no
  domínio; exceção do Jackson).

- [x] **4. Checkpoint.** `mvn clean verify` verde. Marcar passos, commits, push.
  - Feito: `mvn clean verify` com 285 testes verdes. `DependencyRuleChecker` estendido;
    `DependencyRuleTest` cobre libs externas/rede/disco barradas e Jackson/JDK-seguro
    permitidos; o varrimento do código real segue com lista vazia.
