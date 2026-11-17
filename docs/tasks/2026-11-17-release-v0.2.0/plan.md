# Plano — Evidências atualizadas, build-order marcado e release v0.2.0

Spec: `spec.md` nesta pasta. Branch: `release-v0.2.0`, a partir do `main` em `3a4bbe3` (com o
PR #88). Só muda documentação e a versão, então não há ciclo red/green.

## Passos

- [x] **1. Versão 0.2.0.** `pom.xml` e o exemplo de tag no README. Depois,
  `mvn clean verify`, para gerar o JAR das capturas.
- [x] **2. Capturas no lab.**
  - Resetar o lab e criar uma sessão no DVWA.
  - Scan de SQLi e scan sem `--module`.
  - Gabarito de 1 caso com o Ollama parado, para mostrar o `ERRO`.
- [x] **3. Reescrever `docs/evidencias.md`** com as capturas e o `summary.csv` da referência
  pós-#83.
- [x] **4. `docs/build-order.md`**: marcar os 4 itens.
- [x] **5. `mvn clean verify`**, commit, PR.
- [ ] **6. Depois do merge:** tag `v0.2.0` no `main`, `git push origin v0.2.0` e conferir o
  workflow `Release` e os arquivos do Release.
