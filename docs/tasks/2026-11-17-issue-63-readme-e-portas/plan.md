# Plano — README e portas do laboratório (issue #63)

Spec: `spec.md` nesta pasta. Branch: `issue-63-readme-e-portas`, a partir do `main` em `bda75fd`.

## Passos

- [x] **1. Portas só em localhost (red → green).**
  - Teste: `LabComposeTest#portasSoEmLocalhost`.
  - Red: a asserção falha, porque a porta está como `"8080:80"`.
  - Green: `127.0.0.1:` nas duas portas, mais o comentário no compose.
  - Verificar: `mvn test -Dtest=LabComposeTest`.
- [x] **2. README:**
  - "Java 21 ou mais novo";
  - o comando para conferir o digest;
  - a instalação do Ollama sem sudo;
  - a nota de que o laboratório só escuta em `127.0.0.1`.
- [x] **3. `mvn clean verify`.**
- [x] **4. Checkpoint manual** (lab real):
  - subir o laboratório com o compose novo;
  - conferir as portas com `docker compose ps`;
  - `curl` nos dois alvos;
  - `scan` contra o DVWA e contra o Juice Shop (`--module xss`);
  - o comando do digest devolve `1`.

## Resultado

- **Passo 1.** O red veio de `porta exposta fora de localhost: 8080:80`; com a mudança, green.
- **Passo 3.** `mvn clean verify`: 402 testes, 0 falhas, BUILD SUCCESS.
- **Passo 4. Checkpoint** em 2026-11-17, lab real, com o compose novo:
  - `docker compose ps` mostra `127.0.0.1:8080->80` e `127.0.0.1:3000->3000`, e o `ss -ltn`
    só escuta em `127.0.0.1`;
  - o `curl` em `localhost:8080/login.php` e em `localhost:3000/` deu `200`;
  - `scan` no DVWA sem `--module`: igual à #58 (3 achados, 1 erro do `ApiAuthModule`, saída 2);
  - Juice Shop `--module xss`: 0 achados (o único XSS refletido do gabarito ali é o negativo
    `js-xss-redirect-escaped`);
  - Juice Shop `--module xss-dom`: 1 achado, o que mostra que o Chrome alcança o alvo com a
    porta só em IPv4;
  - o comando do digest do README devolveu `1`.
