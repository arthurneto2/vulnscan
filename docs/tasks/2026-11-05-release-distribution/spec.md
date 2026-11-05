# Spec — Distribuição por GitHub Release

## Objetivo

Permitir que uma pessoa baixe uma versão pronta do vulnscan pela página de Releases,
sem instalar Maven. O Release deve oferecer o JAR executável e um pacote completo com
os arquivos do projeto necessários para usar o scanner, o laboratório e o benchmark.

## Escopo

### `.github/workflows/release.yml`

- Disparar quando uma tag no formato `v*` for publicada.
- Usar Ubuntu, checkout, Java 21 Temurin e Maven.
- Executar `mvn -B -ntp clean verify`.
- Gerar `target/vulnscan.jar`.
- Criar `dist/vulnscan-<versão>.tar.gz` contendo:
  - `vulnscan.jar`;
  - `lab/docker-compose.yml`;
  - `experiments/ground_truth.yaml`;
  - `README.md`;
  - `docs/cli.md`;
  - `docs/ground-truth.md`;
  - `docs/experiment.md`;
  - `LICENSE`, se existente.
- Publicar o JAR e o pacote como artefatos de um GitHub Release usando a tag recebida.

O pacote não inclui as imagens Docker, o runtime Java, o Ollama ou o modelo de IA:
esses componentes são pré-requisitos externos e não devem ser embutidos no repositório
ou no Release.

### Documentação

Atualizar o README com o fluxo de download do JAR/pacote, execução com `java -jar`,
pré-requisitos e criação de uma versão por tag.

## Fora do escopo

- Publicar imagens Docker.
- Empacotar Java, Docker, Ollama ou o modelo Mistral.
- Builds para múltiplos sistemas operacionais.
- Alterar a lógica do scanner ou os contratos Java.

## Critérios de aceite

1. Existe um workflow de release acionado por tags `v*`.
2. O workflow executa `mvn clean verify` com Java 21 antes de publicar.
3. O Release contém `vulnscan.jar` e `vulnscan-<versão>.tar.gz`.
4. O pacote contém os arquivos do scanner, laboratório, benchmark e documentação
   especificados.
5. O README explica como baixar, executar e criar uma versão.
6. Um teste automatizado valida a estrutura e os gatilhos do workflow offline.
