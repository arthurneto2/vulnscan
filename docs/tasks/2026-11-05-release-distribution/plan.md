# Plano — Distribuição por GitHub Release

Spec: `spec.md` nesta pasta.

## Passos

- [x] **1. Teste do workflow (red).** Criar `ReleaseWorkflowTest` para ler
  `.github/workflows/release.yml` e exigir o gatilho de tag, Java 21, `clean verify`,
  geração do JAR e publicação dos dois arquivos.
- [x] **2. Workflow (green).** Criar `.github/workflows/release.yml` com build,
  montagem do pacote e publicação no GitHub Release.
- [x] **3. Documentação.** Atualizar o README com instalação via Release, conteúdo
  distribuído, pré-requisitos externos e fluxo para criar uma tag.
- [x] **4. Verificação local.** `ReleaseWorkflowTest` passou; `mvn package -DskipTests`
  gerou o JAR e o pacote foi validado com `tar -tzf`. O `mvn clean verify` executou
  287 testes, mas 3 testes Selenium falharam porque o ambiente não conseguiu obter
  o ChromeDriver sem acesso à internet.
- [x] **5. Checkpoint.** A implementação está pronta; o primeiro
  Release deve ser criado com uma tag `vX.Y.Z`.
