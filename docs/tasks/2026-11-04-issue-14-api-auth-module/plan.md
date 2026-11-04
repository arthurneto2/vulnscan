# Plano — ApiAuthModule (issue #14)

Spec: `spec.md` nesta pasta. Branch: `issue-14-api-auth-module`.

Cada passo: teste primeiro (red por asserção), mínimo para passar (green), refactor.

## Passos

- [x] **1. `ApiSession`/`AuthSession`, `AuthMode`, `AuthProbe`.** Records/enum/interface e
  `AuthSessionTest` (campos não nulos → NPE). `mvn test -Dtest=AuthSessionTest`.

- [x] **2. `ApiAuthModule`: requisições por modo.** `ApiAuthModuleTest` (fakes):
  `#typeEBrokenAuth`, `#noneSemAuthorization`, `#userTokenComBearer`,
  `#outherResourceTrocaIdEUsaBearer`, `#autenticaUmaVezESomenteSePrecisa`,
  `#semProbeComTokenNaoChamaSessao`.

- [x] **3. Vereditos e `Finding`.** `#evidenciaPorProbe`, `#positivoViraFindingDoTipoDoProbe`
  (HIGH), `#cadaProbeEIndependente` (não encerra), `#misturaTiposNosFindings`.

- [x] **4. Erros e validações.** `#erroDoHttpPropaga`, `#erroDaSessaoPropaga`,
  `#construtorRejeitaNulos`, `#construtorRejeitaProbesVazios`. Javadoc. `docs/contracts.md`.

- [x] **5. `JuiceShopSession` (adaptador).** `JuiceShopSessionTest` com `HttpServer` do JDK:
  `#registraELogaDevolvendoTokenEBid`, `#loginSemTokenLancaIllegalState`. Jackson no parse.

- [ ] **6. `Container` + CLI.** `ContainerTest#apiNosModulosDisponiveis`. `docs/cli.md`.

- [ ] **7. Checkpoint.** `mvn clean verify` verde. Lab: `scan --module api` no Juice Shop
  (achados de BROKEN_AUTH e BROKEN_ACCESS_CONTROL; negativos não). Marcar a Fase 4 no
  `build-order`. Marcar passos, commits, push.
