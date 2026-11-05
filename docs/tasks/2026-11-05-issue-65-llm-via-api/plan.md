# Plano — Opção de IA via API (`--llm api`, issue #65)

Spec: `spec.md` nesta pasta. Branch: `issue-65-llm-via-api`, a partir da `main` (já com o
modelo de 14B e o `ollamaTimeout` do PR #66).

## Passos

- [x] **0. Issue.** #65 aberta; pasta e branch com o número.

- [x] **1. Adaptador: requisição.**
  Red: `OpenAiCompatibleClientTest#enviaChatCompletionsComBearerEParametros` (`HttpServer` em
  porta efêmera captura método, path, header `Authorization` e corpo). Com um esqueleto vazio
  para compilar, falhou por asserção (`expected POST but was null`).
  Green: `OpenAiCompatibleClient(HttpClient, URI baseUrl, String model, String apiKey)` monta
  e envia o corpo. A URL base do teste termina em `/v1`, como a da NVIDIA; o path é
  concatenado, porque `URI.resolve("chat/completions")` descartaria o `/v1`.
  Verificar: `mvn test -Dtest=OpenAiCompatibleClientTest`.

- [x] **2. Adaptador: resposta e erros.**
  Red (7 falhas por asserção): `devolveConteudoDaPrimeiraEscolha`,
  `statusDeErroLancaComStatusESemVazarChave` (parametrizado: 401, 429, 500; o corpo de erro
  **ecoa a chave**, como alguns provedores fazem, e a mensagem não pode contê-la),
  `respostaSemChoicesLanca`, `respostaQueNaoEJsonLanca`, `construtorRejeitaNulos`.
  Green: lê `choices[0].message.content`; mensagens de erro com status e trecho do corpo
  (≤ 200 caracteres) com a chave trocada por `***`. As exceções do `JdkHttpClient` não
  incluem headers, então a chave também não vaza por timeout.
  `mvn clean verify`: 314 testes, 0 falhas.

- [x] **3. `ApiLlmConfig`.**
  Red (5 falhas por asserção): `ApiLlmConfigTest#soChaveUsaPadroesDaNvidia`,
  `variaveisSobrescrevemPadroes`, `variavelEmBrancoContaComoAusente`,
  `semChaveLancaErroDeUsoCitandoAVariavel`, `toStringMascaraAChave`.
  Green: record com `fromEnv(Map<String, String>)` e `toString` com `apiKey=***`. Sem chave
  lança `InvalidScanRequestException` (a CLI já a traduz para código 1). O modelo padrão é
  provisório (`nvidia/nemotron-3.5-lightning-30b-a3b`) até o passo 9.
  `mvn clean verify`: 319 testes, 0 falhas.

- [ ] **4. `Config.ollamaTimeout` → `llmTimeout`.** Refactor com os testes verdes
  (`ConfigTest`, `ContainerTest`); atualizar `docs/contracts.md`.

- [ ] **5. App: escolha do backend.**
  Red: `ContainerTest#llmApiUsaProvedorCompativelComOpenAi`. O `HttpServer` local responde
  `/v1/chat/completions` e o `/` imita o DVWA; o `Container` recebe o mapa de ambiente com URL
  local e chave falsa; scan `sqli` com `detector=llm` e `llm=api` → `Achados: 1`.
  `ContainerTest#semLlmUsaOllama` (protege o padrão).
  Green: campo `llm` em `ScanRequest`/`BenchmarkRequest` (construtores antigos mantidos); o
  `Container(Config, Map<String, String> env)` monta o `LlmClient` conforme o `llm`.
  `new Container(config)` delega com `System.getenv()`.

- [ ] **6. CLI.**
  Red: `ScanCommandTest#llmApiSemChaveSaiComUmECitaVariavel`, `llmInvalidoSaiComUm`,
  `BenchmarkCommandTest#aceitaLlmApi` (com ambiente falso).
  Green: `@Option --llm` nos dois comandos, que só repassam o valor no pedido; erro de
  configuração vira código 1.

- [ ] **7. Docs.** README (chave da NVIDIA, variáveis, aviso de dados), CLAUDE.md (regra da
  IA), `docs/cli.md`, `docs/llm-contract.md`, `docs/contracts.md`.

- [ ] **8. `mvn clean verify`.**

- [ ] **9. Checkpoint empírico** (precisa de `VULNSCAN_LLM_API_KEY` do usuário, exportada no
  shell dele; não passa pelo repositório nem pelos logs). Primeiro uma chamada de teste para
  confirmar que a chave faz inferência (relatos de chave nova com 404). Depois o benchmark,
  3 execuções por modelo, com `nemotron-3.5-lightning-30b-a3b` e `nemotron-3-ultra-550b-a55b`,
  lab resetado e banco do DVWA criado antes de cada execução. Tabela em `docs/experiment.md`;
  o vencedor vira o padrão em `ApiLlmConfig`. Contar os vereditos "LLM indisponível" por `429`.
