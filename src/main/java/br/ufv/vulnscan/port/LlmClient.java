package br.ufv.vulnscan.port;

/**
 * Porta de saída para o modelo de IA. Adaptador padrão: adapter/llm/OllamaClient
 * (POST http://localhost:11434). Recebe um prompt pronto e devolve o texto cru do modelo
 * (espera-se JSON — ver docs/llm-contract.md). O parse fica no LlmDetector, não aqui.
 */
public interface LlmClient {
    String complete(String prompt);
}
