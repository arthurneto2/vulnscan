# Contrato da camada de IA

Define exatamente o que vai para o modelo e o que volta. O objetivo é um veredito
**estruturado e determinístico de parsear**, não texto livre.

## Fluxo

`LlmDetector.judge(Evidence)` monta um prompt → chama `LlmClient.complete(prompt)` →
recebe texto cru → faz parse para um `Verdict`. O adaptador (`OllamaClient`) não conhece
esse formato; ele só fala HTTP com o Ollama.

## Modelo

- Runtime: Ollama local, endpoint `POST http://localhost:11434/api/generate`.
- Modelo fixado: `mistral:7b-instruct` (ou `qwen2.5:7b-instruct`). A versão exata fica em
  `app/Config.java` e no README. Fixar a versão é o que torna o experimento reproduzível.
- Parâmetros: `temperature` baixo (ex.: 0.1) para reduzir variação; `format: "json"` para
  forçar saída JSON; `stream: false`.

## Prompt (estrutura)

O `LlmDetector` monta um prompt com três partes:

1. Instrução de papel: "Você é um analisador de segurança. Decida se a resposta HTTP
   indica a vulnerabilidade do tipo X. Responda só com JSON no formato especificado."
2. O tipo de falha em teste (`VulnType`) e o payload aplicado.
3. A evidência: método + URL, trecho relevante dos headers, e o corpo da resposta
   (truncado a um limite, ex.: 4 KB, para não estourar o contexto do modelo).

Não mande a resposta HTTP inteira sem truncar — modelos pequenos têm janela limitada.

## Saída esperada (JSON)

O modelo deve devolver exatamente:

```json
{
  "vulnerable": true,
  "confidence": 0.82,
  "rationale": "A resposta contém um erro de sintaxe SQL do MySQL, indicando que o payload chegou ao banco."
}
```

Mapeamento para `Verdict`: os três campos + `detector = "llm"`.

## Robustez (obrigatória)

- **Parse defensivo.** O modelo pode devolver JSON cercado por texto ou malformado.
  Extraia o primeiro bloco `{...}` e parseie; se falhar, devolva
  `new Verdict(false, 0.0, "resposta do LLM não parseável", "llm")` e registre o ocorrido.
  Nunca deixe uma exceção de parsing derrubar o scan.
- **Timeout.** `OllamaClient` tem timeout (ex.: 30 s). Se estourar, trate como veredito de
  baixa confiança, não como erro fatal.
- **Não determinismo.** Mesmo com temperatura baixa, o LLM varia. O experimento roda N
  vezes e reporta a variância; não assuma saída idêntica entre execuções.

## Onde a IA agrega (e o limite)

Agrega reduzindo falso positivo: uma mensagem de erro no corpo nem sempre é
vulnerabilidade, e o modelo lê o contexto. Também gera a justificativa técnica de cada
achado. O veredito do LLM é sempre comparado ao gabarito — nunca tratado como verdade.
