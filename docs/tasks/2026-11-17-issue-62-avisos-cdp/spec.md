# Spec — Silenciar os avisos de CDP do Selenium (issue #62)

## Objetivo

Cada sessão do Chrome (módulos `xss-dom` e `xss-stored`) imprime 4 linhas de aviso no
stderr, porque o Selenium 4.25.0 não tem a implementação de CDP do Chrome 157. No checkpoint da
#58, um `scan` contra o DVWA deu **40 linhas** de aviso. Isso polui a saída da CLI e as
evidências. O vulnscan não usa CDP: usa WebDriver clássico, `executeScript` e alertas.

Os avisos vêm de dois loggers do `java.util.logging`:

- `org.openqa.selenium.devtools.CdpVersionFinder`: `Unable to find version of CDP to use for …`
- `org.openqa.selenium.chromium.ChromiumDriver`: `Unable to find CDP implementation matching …`

## Decisão: silenciar, e não atualizar o Selenium

Atualizar o `selenium-java` só adia o problema. Cada versão traz o CDP de poucas versões do
Chrome, e o Chrome do sistema se atualiza sozinho. Os avisos voltariam na próxima atualização,
e ainda haveria o risco de uma dependência nova no runtime. Silenciar os dois loggers é
determinístico e não muda nenhum comportamento, já que o CDP não é usado.

## Escopo

1. **`SeleniumBrowser`**: um bloco estático põe o nível `SEVERE` nos **dois loggers acima**,
   pelo nome exato, e não no pacote inteiro. Os `Logger`s ficam em campos `static final`,
   porque o JUL guarda referências fracas e o nível se perderia se o logger fosse coletado
   pelo GC. Avisos de outras partes do Selenium continuam aparecendo.
2. Javadoc do `SeleniumBrowser` com o motivo.

### Fora do escopo

- Atualizar o Selenium, pelo motivo acima.
- Silenciar os `WARNING` do Selenium como um todo.
- Mudar `docs/evidencias.md`, que guarda evidências já coletadas.

## Contratos tocados

Nenhum.

## Critérios de aceite

1. `SeleniumBrowserTest#avisosDeCdpSilenciados`. Depois de carregar a classe
   `SeleniumBrowser`, os loggers `org.openqa.selenium.devtools.CdpVersionFinder` e
   `org.openqa.selenium.chromium.ChromiumDriver` têm nível `SEVERE`, e o logger do pacote
   `org.openqa.selenium` não foi alterado. O teste não precisa do Chrome.
2. `mvn clean verify` verde.
3. **Checkpoint manual**: `scan` contra o DVWA sem `--module` (lab real). O stderr fica sem
   nenhuma linha `CDP`, e o resultado é o da #58: 3 achados e 1 erro do `ApiAuthModule`.
