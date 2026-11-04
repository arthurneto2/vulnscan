# Spec — Teste da regra de dependência: domain sem bibliotecas externas (issue #29)

Referência: issue #29 (desdobrada da #2). Estende o `DependencyRuleChecker` (hoje só pacotes
do projeto) para também proibir **bibliotecas externas** e **pacotes de rede/disco do JDK**
no `domain/`.

## Conflito resolvido (decisão)

A #18 pôs Jackson no `LlmDetector` (domínio) para parsear o JSON do LLM, com aprovação. Logo
a regra nova **não** proíbe Jackson no domínio: usa uma **allowlist** com
`com.fasterxml.jackson`. O critério original da issue ("falha se referenciar
`com.fasterxml.jackson`") é ajustado aqui, com a justificativa registrada. A regra pega o que
importa de verdade: Selenium, picocli, jsoup e qualquer lib não prevista.

## Escopo — estender `DependencyRuleChecker` (camada `domain/`)

Além da regra de pacotes do projeto (inalterada), sobre cada `import` de arquivo em
`domain/`:

- **Proibido:** qualquer `import` cujo pacote **não** seja `java.*` nem
  `br.ufv.vulnscan.*`, **exceto** a allowlist `com.fasterxml.jackson` (parse do veredito do
  LLM, decisão da #18).
- **Proibido (rede/disco do JDK):** `java.net.http`, `java.nio.file` e `java.io` de disco
  (`java.io.File`, `java.io.FileReader`/`Writer`, `java.io.FileInputStream`/`OutputStream`).
  O domínio fala com o mundo só pelas portas; estes pacotes são de adaptador.
- **Permitido no JDK (documentado):** `java.net.URI`/`URLEncoder` (valores, não conexão),
  `java.nio.charset`, `java.time`, `java.util.*` (inclui `regex`, `function`).

A verificação lê a **linha `import`** (prefixo do pacote), então `java.net.http` é barrado
sem barrar `java.net.URI`. `port/` fica fora desta regra nova (usa `java.nio.file.Path` na
assinatura do `ReportWriter`); a regra de projeto do `port/` continua como está.

## Fora do escopo

- Mudar o domínio (ele já está conforme: só JDK seguro + Jackson). Checar `adapter`/`app`/`cli`.

## Contratos tocados

Nenhum de código. `docs/architecture.md` ganha uma nota: domínio sem libs externas, com a
exceção allowlisted do Jackson, e sem rede/disco do JDK.

## Critérios de aceite

1. `DependencyRuleTest` (estendido) **passa no código atual** (domínio: só JDK seguro +
   Jackson).
2. O checker **falha** (em teste dedicado, com fontes sintéticos ou asserção direta sobre o
   método) se o domínio importar `org.openqa.selenium`, `picocli`, `org.jsoup`,
   `java.net.http` ou `java.nio.file`.
3. Jackson (`com.fasterxml.jackson`) no domínio **não** é violação (allowlist).
4. `port/` continua válido (usa `java.nio.file.Path`).
5. `mvn clean verify` verde.
