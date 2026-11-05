# Plano — LlmDetector com 100% de falso positivo (issue #61)

Spec: `spec.md` nesta pasta. Branch: `issue-61-llm-falso-positivo`.

## Passos

- [x] **1. Contexto completo no prompt.**
  Red: `LlmDetectorTest#promptTrazContextoCompleto`. A evidência tem descrição do payload,
  header na requisição, status 401 e `elapsedMillis` 5123; o prompt capturado deve conter cada
  um desses itens. Falhou em "descrição do payload".
  Green: `prompt()` reescrito em inglês com os campos novos. A marca `[truncado]` foi mantida.

- [x] **2. Critério por tipo e aviso anti-viés.**
  Red: `promptDeSqliTrazCriterioDeSqliENaoODeXss`, `promptDeXssTrazCriterioDeXss`,
  `promptDeAuthDizQueARequisicaoFoiIndevida`, `promptDizQueNaoVulneravelEComumEPedeEvidenciaPrimeiro`.
  Os 4 falharam por asserção.
  Green: `criterion(VulnType)` com `switch`; frase "\"vulnerable\": false is common"; saída
  com `evidence`, `rationale`, `vulnerable`, `confidence` nessa ordem; trecho ≤ 120 caracteres.

- [x] **3+4. Grounding** (os dois passos foram unidos: os testes de "manter" já passavam antes,
  porque o código antigo ignorava `evidence`, então o red real era o de "rebaixar").
  Red: `vulneravelSemEvidenciaViraNaoVulneravel` e
  `vulneravelComEvidenciaAusenteDoCorpoViraNaoVulneravel` falharam (`expected false, was true`).
  Os testes de proteção `vulneravelComEvidenciaNoCorpoEMantido`,
  `evidenciaComDiferencaDeEspacoEMaiusculaContaComoPresente` e `naoVulneravelNaoEAlterado`
  foram escritos junto.
  Green: `parse` devolve `Parsed(verdict, evidence)`; `ground()` rebaixa para
  `Verdict(false, 0.2, "veredito do LLM sem evidência na resposta: <rationale>", "llm")`.
  **Desvio:** a normalização **remove** todo espaço em branco (em vez de colapsar), porque o
  modelo também acrescenta espaço onde o corpo não tem (ex.: JSON reformatado). Isso é
  coerente com a spec ("ignora diferença de espaços"). Os 3 testes antigos que usavam
  `vulnerable: true` sem `evidence` passaram a citar um trecho do corpo.

- [x] **5. `num_predict` no Ollama.**
  Red: `OllamaClientTest#corpoLevaTetoDeTokens` (`expected 256, was -1`).
  Green: `MAX_TOKENS = 256` em `options`; Javadoc atualizado.

- [x] **6. Docs.** `docs/llm-contract.md` (parâmetros, prompt, saída, grounding) e
  `docs/experiment.md` (seção "Limitações").

- [x] **7. Verificação.** `mvn clean verify`: 298 testes, 0 falhas, BUILD SUCCESS.

- [x] **8. Checkpoint empírico (2026-11-05).** Ollama 0.40.14 local, RTX 4050,
  `mistral:7b-instruct-v0.3-q4_0`. Três execuções; antes de cada uma: `down`/`up` do lab e
  banco do DVWA criado pelo `setup.php` (#59). 14 casos × 3 = 42 julgamentos por detector.

  | Detector | VP | FP | VN | FN | Precisão | Recall | F1 | Taxa FP | Mediana |
  |---|---|---|---|---|---|---|---|---|---|
  | heuristic | 24 | 0 | 18 | 0 | 1.000 | 1.000 | 1.000 | 0% | 23 ms |
  | llm — antes (issue) | 24 | 18 | 0 | 0 | 0.571 | 1.000 | 0.727 | 100% | 2340 ms |
  | llm — depois | 15 | 8 | 10 | 9 | 0.652 | 0.625 | 0.638 | **44%** | 3113 ms |

  **Meta: parcialmente atingida.** Taxa de FP ≤ 50% ✓; recall ≥ 0.75 ✗ (0.625); F1 piorou.

  Por caso (3 execuções):
  - Viraram VN (corrigidos): `dvwa-sqli-impossible`, `dvwa-xss-reflected-impossible`,
    `js-auth-users-protected` (3/3); `js-bac-order-history` (1/3).
  - Continuam FP: `dvwa-xss-stored-impossible` (3/3), `js-xss-redirect-escaped` (3/3),
    `js-bac-order-history` (2/3). Nesses, a evidência citada existe na resposta (ex.: "malicious
    activity" no 403); o erro é de raciocínio do modelo, que o grounding não pega por desenho.
  - Novos FN: `js-auth-admin-config` (3/3), `js-idor-basket` (3/3), `js-bac-users-role` (2/3),
    `js-xss-dom-search` (1/3).

  **Causa dos novos FN (reprodução em jshell com o `LlmDetector` real):** o modelo acerta
  (`vulnerable: true`, rationale correto), mas o **grounding** rebaixa. Em respostas JSON o
  modelo resume em vez de copiar: `{"id":12,"UserId":31}` (o corpo tem
  `{"id":12,"coupon":null,"UserId":31,…`), `{"data": [...]}` e `"OWASP Juice Shop!"`. A
  comparação literal funciona para HTML/texto (barrou o XSS alucinado na investigação), mas é
  estrita demais para JSON. É defeito do desenho do grounding, não do modelo.

- [x] **9. Grounding para evidência JSON** (aprovado pelo usuário depois do passo 8; spec
  revisada). Red: `evidenciaJsonResumidaComParesDoCorpoContaComoPresente` e
  `evidenciaEntreAspasEDesembrulhada` falharam (rebaixados). As proteções
  `evidenciaJsonComParInexistenteViraNaoVulneravel`, `evidenciaJsonSemParEscalarNaoConta` e
  `payloadCruEntreAspasContinuaRebaixadoSeOCorpoEscapou` foram escritas junto e já passavam.
  Green: `grounded()` tenta a forma literal; se falhar e o trecho for JSON, usa a string
  desembrulhada ou os pares escalares (`collectScalarPairs`). Docs: `llm-contract.md`,
  `experiment.md` (ameaça à validade) e spec.

- [x] **10. `mvn clean verify` e novo checkpoint empírico** (mesmo procedimento do passo 8).
  `mvn clean verify`: 303 testes, 0 falhas, BUILD SUCCESS.

  | Detector | VP | FP | VN | FN | Precisão | Recall | F1 | Taxa FP | Mediana |
  |---|---|---|---|---|---|---|---|---|---|
  | heuristic | 24 | 0 | 18 | 0 | 1.000 | 1.000 | 1.000 | 0% | 22 ms |
  | llm — antes (issue) | 24 | 18 | 0 | 0 | 0.571 | 1.000 | 0.727 | 100% | 2340 ms |
  | llm — passo 8 (grounding literal) | 15 | 8 | 10 | 9 | 0.652 | 0.625 | 0.638 | 44% | 3113 ms |
  | llm — final | 24 | 6 | 12 | 0 | 0.800 | 1.000 | 0.889 | **33%** | 3227 ms |

  **Meta atingida:** taxa de FP 33% (≤ 50%) e recall 1.0 (≥ 0.75). Resultado idêntico nas 3
  execuções (variância zero por caso).
  FP restantes, 3/3 cada: `dvwa-xss-stored-impossible` e `js-xss-redirect-escaped`. Os dois
  são XSS com a saída escapada, o limite do modelo com entidades HTML descrito na spec (causa 5).
  Não há correção no código sem copiar a regra da heurística; o próximo passo natural é
  comparar com outro modelo.

- [x] **11. Bug do parse com chave dentro de string** (achado na comparação com
  `qwen2.5:14b-instruct`). O modelo devolveu JSON válido e correto, mas o veredito saiu "resposta
  do LLM não parseável". `firstJsonBlock` contava `{`/`}` sem saber se estava dentro de uma
  string, e o `evidence` citava `"…\"data\":{\"id\":11"`, com chave aberta dentro da string. O
  bug já existia; o campo `evidence` o tornou alcançável.
  Red: `chaveDesbalanceadaDentroDeStringNaoQuebraOParse` e
  `chaveFechandoDentroDeStringNaoEncerraOBlocoAntes` falharam (não parseável; confiança 0.0).
  Green: `firstJsonBlock` acompanha aspas e escape. `mvn clean verify`: 305 testes, 0 falhas.
  Os números do passo 10 foram medidos **antes** desta correção; a comparação de modelos usa o
  código corrigido.

- [x] **12. Modelo padrão de 14B e timeout próprio do Ollama** (pedido do usuário depois da
  comparação; spec revisada, item 5).
  Red: `ConfigTest#defaultsFixaTimeoutModeloEUrlDoOllama` passou a esperar
  `qwen2.5:14b-instruct` e `ollamaTimeout` de 120 s (falhou: `was mistral:…`).
  `ContainerTest#llmUsaTimeoutProprioMaiorQueODoAlvo`: Ollama falso demora 600 ms, alvo com
  300 ms e Ollama com 5 s; com o cliente compartilhado o veredito estourava (`Achados: 0`).
  O servidor de teste ganhou um pool de threads: com uma thread só, o `sleep` do Ollama falso
  travava a requisição seguinte ao alvo (erro de setup, não de asserção).
  Green: `Config` com `ollamaTimeout`; `Container` cria um `JdkHttpClient` próprio para o
  `OllamaClient`. Docs: README, CLAUDE.md (mínimo de 14B), `llm-contract.md` (modelo, digest,
  timeout), `contracts.md`, `evidencias.md`, `experiment.md` ("Escolha do modelo").
  `mvn clean verify`: 306 testes, 0 falhas.
