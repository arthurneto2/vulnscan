# Plano — Registrar a rodada de referência após as #60–#83

- Spec: `spec.md` nesta pasta.
- Branch: `ref-pos-83`, a partir do `main` em `dc7a6ec`.
- Só muda documentação, então não há ciclo red/green.

## Passos

- [x] **1. Conferir os números.** Recalcular a tabela a partir dos `run-*.csv` das duas
  pastas:
  - VP/FP/VN/FN/ERRO;
  - F1 por execução;
  - mediana e máximo do tempo;
  - chamadas ao Ollama, contadas no `ollama.log`.
- [x] **2. Nova seção em `docs/experiment.md`**, depois da seção da #72: tabela, protocolo,
  commits, achados e a nota sobre o Ultra (commit `cbc1612` e por que a rodada vale).
- [x] **3. A seção da #72 passa a histórico.** Trocar o título e acrescentar uma linha
  apontando para a seção nova.
- [x] **4. README.** Não cita números da referência: nada a mudar.
- [x] **5. `mvn clean verify`.** 389 testes, 0 falhas, BUILD SUCCESS.
