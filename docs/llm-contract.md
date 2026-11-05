# Contrato da camada de IA

Define exatamente o que vai para o modelo e o que volta. O objetivo é um veredito
**estruturado e determinístico de parsear**, não texto livre.

## Fluxo

`LlmDetector.judge(Evidence)` monta um prompt → chama `LlmClient.complete(prompt)` →
recebe texto cru → faz parse para um `Verdict`. O adaptador (`OllamaClient`) não conhece
esse formato; ele só fala HTTP com o Ollama.

## Modelo

- Runtime: Ollama local, endpoint `POST http://localhost:11434/api/generate`.
- Modelo fixado: `mistral:7b-instruct` (Apache-2.0). A tag exata é
  `mistral:7b-instruct-v0.3-q4_0`, fixada em `app/Config.java` (`Config.defaults()`) e no
  README. Fixar a versão é o que torna o experimento reproduzível.
- Digest de reprodutibilidade (preencher após `ollama pull`, de `ollama list --digests`):
  `sha256:<preencher após ollama pull>`.
- Parâmetros: `temperature: 0.1` para reduzir variação; `num_predict: 256` (teto de tokens
  gerados: sem ele, pedir um trecho de JSON longo fez o modelo entrar em repetição e o
  Ollama abortar com `token repeat limit reached`); `format: "json"` para forçar saída JSON;
  `stream: false`.

## Prompt (estrutura)

O prompt é em inglês (o Mistral 7B é mais estável em inglês) e tem quatro partes:

1. **Papel e aviso anti-viés:** analista revisando UM teste automatizado; "não vulnerável"
   é uma resposta comum e correta quando falta evidência; não concluir pela simples
   presença do payload ou pelo nome do teste.
2. **Tipo e critério:** o `VulnType` e um critério de evidência em texto para esse tipo
   (SQLi: erro de banco causado pelo payload ou mudança clara na lógica, como atraso;
   XSS: payload com `<` e `>` literais em resposta HTML, e entidades `&lt; &gt;` significam
   escapado; auth/controle de acesso: a requisição foi feita indevida de propósito, e só 2xx
   com dado protegido é vulnerável). O critério é contexto para o modelo julgar; o código
   não aplica regra por tipo ao veredito.
3. **Evidência completa:** payload, descrição do payload (ex.: "probe de NONE (requisição
   sem token)"), método + URL, headers da requisição, status, tempo de resposta (ms),
   headers da resposta e corpo truncado a 4 KB.
4. **Formato da saída**, com os campos na ordem abaixo.

Não mande a resposta HTTP inteira sem truncar — modelos pequenos têm janela limitada.

## Saída esperada (JSON)

O modelo deve devolver, **nesta ordem** (evidência e raciocínio antes do veredito):

```json
{
  "evidence": "You have an error in your SQL syntax",
  "rationale": "A resposta contém um erro de sintaxe SQL do MySQL, indicando que o payload chegou ao banco.",
  "vulnerable": true,
  "confidence": 0.82
}
```

`evidence` é um trecho de até 120 caracteres copiado literalmente do corpo da resposta, ou
`""`. A ordem importa: com `vulnerable` primeiro, o modelo se comprometia com o veredito
antes de raciocinar e respondia "vulnerável" em 100% dos casos (issue #61), inclusive com
justificativa contraditória ("401 Unauthorized" e `vulnerable: true`).

Mapeamento para `Verdict`: `vulnerable`, `confidence`, `rationale` + `detector = "llm"`,
depois do grounding abaixo. O parse lê por nome de campo, então a ordem não é exigida no
parse, só pedida no prompt.

## Grounding (obrigatório)

Se `vulnerable == true` e `evidence` estiver vazio, ausente ou não aparecer no corpo da
resposta, o veredito vira
`new Verdict(false, 0.2, "veredito do LLM sem evidência na resposta: <rationale>", "llm")`.
A comparação ignora espaços em branco e maiúsculas e **não** desfaz escape de HTML (o caso
real era o modelo citar `<script>…` cru quando o corpo tinha `&lt;script&gt;…`).

Se o trecho não estiver literalmente no corpo mas for JSON válido, ainda conta como presente
quando: é uma string JSON e o texto desembrulhado está no corpo (ex.: `"OWASP Juice Shop!"`),
ou é objeto/array e **todos** os pares `"chave":valor` escalares citados, em qualquer nível,
estão no corpo, com ao menos um par (ex.: `{"id":12,"UserId":31}` contra
`{"id":12,"coupon":null,"UserId":31,…}`). Motivo: em respostas JSON o modelo resume em vez
de copiar; a regra literal rejeitava vereditos corretos (checkpoint da issue #61). Elisões
como `{"data": [...]}` não contam. Vereditos
`false` não são alterados. A regra é genérica, igual para todos os tipos: o detector "llm"
medido continua sendo o julgamento do modelo, não uma cópia da heurística.

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
