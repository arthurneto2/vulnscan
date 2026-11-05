package br.ufv.vulnscan.app;

import java.net.URI;
import java.util.Map;
import java.util.Objects;

/**
 * Aplicação: configuração da IA via API ({@code --llm api}, issue #65), lida de variáveis de
 * ambiente — nunca de arquivo do repositório nem de argumento da CLI. {@code toString()} mascara
 * a chave.
 *
 * <ul>
 *   <li>{@value #KEY_VAR} — obrigatória;</li>
 *   <li>{@value #URL_VAR} — padrão: NVIDIA NIM ({@code https://integrate.api.nvidia.com/v1});</li>
 *   <li>{@value #MODEL_VAR} — padrão: {@code nvidia/nemotron-3-ultra-550b-a55b}.</li>
 * </ul>
 */
public record ApiLlmConfig(URI baseUrl, String model, String apiKey) {

    public static final String KEY_VAR = "VULNSCAN_LLM_API_KEY";
    public static final String URL_VAR = "VULNSCAN_LLM_API_URL";
    public static final String MODEL_VAR = "VULNSCAN_LLM_API_MODEL";

    private static final String DEFAULT_URL = "https://integrate.api.nvidia.com/v1";
    /** Vencedor do checkpoint da #65 (F1 0,93 estável, 0 FP; o Lightning variou e foi ~4x mais lento). */
    private static final String DEFAULT_MODEL = "nvidia/nemotron-3-ultra-550b-a55b";

    public ApiLlmConfig {
        Objects.requireNonNull(baseUrl, "baseUrl");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(apiKey, "apiKey");
    }

    /** Lê do mapa de ambiente (variável em branco = ausente). Sem chave → erro de uso. */
    public static ApiLlmConfig fromEnv(Map<String, String> env) {
        String key = value(env, KEY_VAR);
        if (key == null) {
            throw new InvalidScanRequestException("--llm api exige a variável de ambiente " + KEY_VAR
                    + " com a chave do provedor (ex.: a da NVIDIA, em build.nvidia.com)");
        }
        String url = Objects.requireNonNullElse(value(env, URL_VAR), DEFAULT_URL);
        String model = Objects.requireNonNullElse(value(env, MODEL_VAR), DEFAULT_MODEL);
        return new ApiLlmConfig(URI.create(url), model, key);
    }

    private static String value(Map<String, String> env, String name) {
        String value = env.get(name);
        return value == null || value.isBlank() ? null : value.strip();
    }

    @Override
    public String toString() {
        return "ApiLlmConfig[baseUrl=" + baseUrl + ", model=" + model + ", apiKey=***]";
    }
}
