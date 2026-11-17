# Plano — README e portas do laboratório (issue #63)

Spec: `spec.md` nesta pasta. Branch: `issue-63-readme-e-portas`, a partir do `main` em `bda75fd`.

## Passos

- [ ] **1. Portas só em localhost (red → green).**
  - Teste: `LabComposeTest#portasSoEmLocalhost`.
  - Red: a asserção falha, porque a porta está como `"8080:80"`.
  - Green: `127.0.0.1:` nas duas portas, mais o comentário no compose.
  - Verificar: `mvn test -Dtest=LabComposeTest`.
- [ ] **2. README:**
  - "Java 21 ou mais novo";
  - o comando para conferir o digest;
  - a instalação do Ollama sem sudo;
  - a nota de que o laboratório só escuta em `127.0.0.1`.
- [ ] **3. `mvn clean verify`.**
- [ ] **4. Checkpoint manual** (lab real):
  - subir o laboratório com o compose novo;
  - conferir as portas com `docker compose ps`;
  - `curl` nos dois alvos;
  - `scan` contra o DVWA e contra o Juice Shop (`--module xss`);
  - o comando do digest devolve `1`.
