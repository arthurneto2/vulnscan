# Spec — Registrar a rodada de referência após as #60–#83

## Objetivo

Registrar em `docs/experiment.md` a nova rodada de referência. A da #72 (14 casos) deixou de
representar o scanner atual por quatro mudanças:

- reset automático entre as execuções (#60);
- o `ERRO` separado do FN/VN (#74);
- o par de XSS de DOM do DVWA, que leva o gabarito a 16 casos (#77);
- o retry em 429 (#79), mais os diálogos no prompt (#81) com a correção da #83.

## Dados (já medidos, fora do git em `experiments/results/`)

Protocolo das duas rodadas: 16 casos × 3 execuções, `--reset`, notebook na tomada, sem
suspensão.

| Rodada | Commit | Pasta | Observação |
|---|---|---|---|
| Qwen 14B (Ollama) | `dc7a6ec` | `2026-11-17-ref83-ollama-qwen14b` | 96/96 chamadas ao Ollama com 200 |
| Ultra (API) | `cbc1612` | `2026-11-17-ref-api-ultra` | 7 `ERRO` por `429 após 4 tentativas` |

A rodada do Ultra é de antes da #83, mas a #83 não muda o prompt dele em nenhum caso:

- nos casos sem navegador, o texto voltou a ser o de antes da #81, e o Ultra já acertava esses
  casos com qualquer um dos dois textos;
- nos casos com diálogos, o prompt é idêntico.

O Ultra não é afetado pelo texto (A/B da #83 e rodada pós-#81), então a rodada vale. Isso fica
anotado no registro.

| Detector | VP | FP | VN | FN | ERRO | F1 | Mediana | Máx. | F1 por execução |
|---|---|---|---|---|---|---|---|---|---|
| heuristic | 27 | 0 | 21 | 0 | 0 | 1.000 | < 0,1 s | 8 s | 1.000 / 1.000 / 1.000 |
| Qwen 14B | 25 | 0 | 21 | 2 | 0 | 0.962 | 18,7 s | 107 s | 1.000 / 0.941 / 0.941 |
| Ultra | 26 | 0 | 15 | 0 | 7 | 1.000 | 17,1 s | 97 s | 1.000 / 1.000 / 1.000 |

## Escopo

1. Nova seção em `docs/experiment.md`: "Rodada após as #60–#83 (resultado de referência)".
   - A tabela acima.
   - O protocolo e os commits.
   - Os achados:
     - o FP do LLM continua em 0%;
     - os dois FN do Qwen são `js-idor-basket`, o mesmo caso instável de antes;
     - o DOM do DVWA dá TP nos dois modelos;
     - o `ERRO` do Ultra fica fora do F1, e o denominador muda.
2. A seção da #72 deixa de ser "resultado de referência" e passa a histórico, como já foi
   feito com a seção das #59/#68.
3. A tabela do Ultra deixa claro que o F1 não conta os 7 `ERRO`. São 41 de 48 julgamentos;
   ninguém deve ler 1.000 como 48/48.
4. README: se ele citar números da referência, atualizar.

### Fora do escopo

- Código e testes: não há mudança de produção, então não há TDD.
- Refazer a rodada do Ultra para tirar os 429. Isso depende da cota do plano gratuito; fica
  registrado como limitação.

## Critérios de aceite

- Os números da seção conferem com os `summary.csv` e `run-*.csv` das pastas citadas.
- Nenhuma seção diz que a referência ainda é a da #72.
- `mvn clean verify` verde. Só muda doc, mas o `GroundTruthFileTest` e o build continuam
  passando.
