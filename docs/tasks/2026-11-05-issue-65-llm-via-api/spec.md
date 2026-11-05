# Spec — Opção de IA via API (`--llm api`, issue #65)

Issue #65 (pedido do usuário em 2026-11-05, depois da issue #61).

## Objetivo

Permitir que quem não tem máquina para rodar o modelo local mínimo (14B, ~9 GB, GPU recomendada)
use o detector "llm" com um modelo hospedado, acessado por API. O padrão continua sendo o
Ollama local. A API também permite comparar o 14B local com modelos maiores no mesmo
experimento.

## Pesquisa (2026-11-05)

- **NVIDIA NIM (build.nvidia.com)** — recomendado como padrão.
  - Gratuito com uma conta do NVIDIA Developer Program, sem cartão, mas com verificação de
    telefone. Endpoint compatível com OpenAI: `https://integrate.api.nvidia.com/v1/chat/completions`
    com `Authorization: Bearer <chave>`.
  - Teto publicado de 40 req/min e 10.000 req/dia por conta. Segundo a NVIDIA, é para
    avaliação e desenvolvimento, não para produção.
  - Modelos gratuitos incluem `nvidia/nemotron-3.5-lightning-30b-a3b`,
    `nvidia/nemotron-3-ultra-550b-a55b`, `openai/gpt-oss-20b`, `google/gemma-4-31b-it` e
    `deepseek-ai/deepseek-v4.1-flash`. O catálogo muda com frequência.
  - **Modelo de segurança:** a NVIDIA não tem um LLM treinado especificamente para
    vulnerabilidades. Ela tem o blueprint *Vulnerability Analysis for Container Security*
    (triagem de CVE em containers, outra tarefa), que usa `nemotron-3-ultra-550b-a55b` como
    agente principal e `nemotron-3.5-lightning-30b-a3b` como pesquisador de CVE. São esses dois
    que avaliamos aqui.
  - **Dados:** "Your input and output will be recorded … to improve NVIDIA products and
    services, including AI models". Mandamos só requisições e respostas do laboratório
    (DVWA/Juice Shop), sem dado real, mas isso tem de constar no README.
- **Cisco Foundation-Sec-8B-Instruct**: o único LLM aberto especializado em segurança que
  encontramos. Não tem API pública hospedada (só os pesos no Hugging Face). Tem 8B, abaixo do
  mínimo de 14B decidido na #61, e a licença do Instruct está como "See NOTICE.md" (não
  confirmada). Fica fora.
- **Outros com plano gratuito e API compatível com OpenAI:** Groq (~30 req/min, 1.000 req/dia),
  Google Gemini (no plano gratuito os dados podem ir para treino), OpenRouter (50 req/dia sem
  crédito) e Cerebras. O mesmo adaptador atende todos, trocando URL e modelo.

## Escopo

1. **Adaptador `adapter/llm/OpenAiCompatibleClient`**, que implementa a porta `LlmClient` (a
   porta não muda). Faz `POST <baseUrl>/chat/completions` com:
   - `Authorization: Bearer <chave>` e `Content-Type: application/json`;
   - corpo `{"model", "messages": [{"role": "user", "content": <prompt>}], "temperature": 0.1,
     "max_tokens": 8192, "stream": false}` (era 256; ver o passo 9 do plano: o raciocínio dos
     modelos Nemotron consome o teto antes da resposta).

   Devolve `choices[0].message.content`. Status fora de 2xx ou resposta sem esse campo lança
   `IllegalStateException` com o status e um trecho do corpo, **nunca a chave**. Não envia
   `response_format`, porque nem todo provedor aceita; o `LlmDetector` já extrai o primeiro
   bloco `{...}`.
2. **Configuração da API por variável de ambiente**, nunca por arquivo do repositório nem por
   argumento da CLI (argumento aparece no histórico do shell e no `ps`):
   - `VULNSCAN_LLM_API_KEY` (obrigatória para `--llm api`);
   - `VULNSCAN_LLM_API_URL` (padrão: `https://integrate.api.nvidia.com/v1`);
   - `VULNSCAN_LLM_API_MODEL` (padrão: o vencedor do checkpoint empírico, entre
     `nvidia/nemotron-3.5-lightning-30b-a3b` e `nvidia/nemotron-3-ultra-550b-a55b`).

   Um record `app/ApiLlmConfig(URI baseUrl, String model, String apiKey)` com
   `fromEnv(Map<String, String>)` (recebe o mapa, para teste sem variável real). O
   `toString()` **mascara a chave**.
3. **CLI:** opção `--llm ollama|api` em `scan` e em `benchmark`, com padrão `ollama`. Valor
   inválido, ou `api` sem `VULNSCAN_LLM_API_KEY`, dá código de saída `1` (erro de uso) e uma
   mensagem que diz qual variável definir. A CLI só traduz o argumento e passa no pedido.
4. **App:** o `Container` ganha `scanService(String llm)` e `benchmarkService(String llm)`
   e monta o `LlmClient` do detector "llm" conforme o backend; os métodos sem argumento usam
   `ollama`. O `Container(Config, Map<String, String> env)` recebe o ambiente (injetável em
   teste); o `Container(Config)` usa `System.getenv()`. **Desvio (passo 5):** a spec previa
   um campo `llm` em `ScanRequest`/`BenchmarkRequest`, mas o backend é escolha de montagem,
   não do caso de uso. Com o campo, o `ScanService` teria de saber construir um `LlmClient`.
   Assim os requests e os serviços não mudam. `Config.ollamaTimeout` passa a se chamar `llmTimeout` (120 s, vale
   para os dois backends).
5. **Docs:**
   - `docs/contracts.md`: requests, `ApiLlmConfig` e `Config`.
   - `docs/llm-contract.md`: o backend API e os parâmetros equivalentes.
   - `docs/cli.md`: a opção `--llm`.
   - README: como obter a chave na NVIDIA, as variáveis e o aviso sobre dados.
   - CLAUDE.md: a regra "A IA é 100% local (Ollama)" vira "local por padrão; API opcional,
     chave só por variável de ambiente".
   - `docs/experiment.md`: a linha do modelo via API na seção "Escolha do modelo".

## Fora do escopo

- Retry ou backoff em `429`: a falha vira veredito "LLM indisponível", como hoje. Se o
  checkpoint mostrar `429` em volume relevante, isso vira issue nova.
- Adaptador nativo da Anthropic ou do Gemini (o Gemini já tem endpoint compatível com OpenAI).
- Arquivo `.env` ou cofre de segredos.
- Gravar no relatório qual backend e modelo foram usados (o `--out` do benchmark identifica a
  execução). Pode virar issue.
- Terceiro detector no benchmark (ex.: "llm-api" ao lado de "llm"): a comparação entre
  backends é feita rodando o benchmark duas vezes.

## Segurança

- A chave só existe em variável de ambiente e em memória. Não aparece em log, em mensagem de
  exceção, em `toString`, em relatório nem no repositório. Os testes verificam isso.
- O scanner continua atacando só `localhost`/lab. A única saída para a internet é a chamada ao
  provedor de IA, e só com `--llm api` explícito.

## Contratos tocados

`LlmClient`, `Detector`, `Verdict`, `Evidence`, `ScanRequest`, `BenchmarkRequest`,
`ScanService` e `BenchmarkService` não mudam. Mudam `Config` (`llmTimeout`) e `Container`
(métodos com `llm`), e entram o `ApiLlmConfig` e o `OpenAiCompatibleClient` (registrados em
`docs/contracts.md`).

## Critérios de aceite

Adaptador (`OpenAiCompatibleClientTest`, `HttpServer` local):

1. Envia `POST /v1/chat/completions` com `Authorization: Bearer <chave>`, e o corpo tem
   `model`, `messages[0].content == prompt`, `temperature == 0.1` e `max_tokens == 8192`.
2. Devolve `choices[0].message.content`.
3. Com status 401/429/500, lança `IllegalStateException` com o status e sem a chave na
   mensagem.
4. Com 200 sem `choices`, lança `IllegalStateException`.

Configuração (`ApiLlmConfigTest`):

5. Com só a chave definida, usa a URL e o modelo padrão.
6. As variáveis de URL e modelo sobrescrevem os padrões.
7. Sem a chave, lança exceção que cita `VULNSCAN_LLM_API_KEY`.
8. O `toString()` não contém a chave.

CLI e app:

9. `scan --llm api` sem a chave sai com código 1 e cita a variável. `--llm xyz` sai com
   código 1.
10. `Container` com `llm=api` e o "provedor" num `HttpServer` local: o detector "llm" usa o
    adaptador novo (o achado aparece no relatório).
11. Sem `--llm`, o comportamento é idêntico ao de hoje (Ollama).

Empírico (precisa da chave do usuário; lab resetado e banco do DVWA criado antes de cada
execução, 3 execuções por modelo):

12. Benchmark com `nemotron-3.5-lightning` e com `nemotron-3-ultra`. O melhor F1 vira o
    modelo padrão, e a tabela entra em `docs/experiment.md` ao lado do Mistral 7B e do Qwen
    14B. Reportar também a mediana de tempo e quantos vereditos falharam por `429`.
