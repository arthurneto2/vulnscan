package br.ufv.vulnscan.app;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Testes da configuração da IA via API, lida de um mapa de ambiente (nunca do ambiente real). */
class ApiLlmConfigTest {

    private static final String KEY = "nvapi-segredo-xyz";

    @Test
    void soChaveUsaPadroesDaNvidia() {
        ApiLlmConfig config = ApiLlmConfig.fromEnv(Map.of("VULNSCAN_LLM_API_KEY", KEY));

        assertEquals(URI.create("https://integrate.api.nvidia.com/v1"), config.baseUrl());
        assertEquals("nvidia/nemotron-3.5-lightning-30b-a3b", config.model());
        assertEquals(KEY, config.apiKey());
    }

    @Test
    void variaveisSobrescrevemPadroes() {
        ApiLlmConfig config = ApiLlmConfig.fromEnv(Map.of(
                "VULNSCAN_LLM_API_KEY", KEY,
                "VULNSCAN_LLM_API_URL", "https://api.groq.com/openai/v1",
                "VULNSCAN_LLM_API_MODEL", "llama-3.3-70b-versatile"));

        assertEquals(URI.create("https://api.groq.com/openai/v1"), config.baseUrl());
        assertEquals("llama-3.3-70b-versatile", config.model());
    }

    @Test
    void variavelEmBrancoContaComoAusente() {
        ApiLlmConfig config = ApiLlmConfig.fromEnv(Map.of(
                "VULNSCAN_LLM_API_KEY", KEY, "VULNSCAN_LLM_API_URL", " ", "VULNSCAN_LLM_API_MODEL", ""));

        assertEquals(URI.create("https://integrate.api.nvidia.com/v1"), config.baseUrl());
        assertEquals("nvidia/nemotron-3.5-lightning-30b-a3b", config.model());
    }

    @Test
    void semChaveLancaErroDeUsoCitandoAVariavel() {
        InvalidScanRequestException e = assertThrows(InvalidScanRequestException.class,
                () -> ApiLlmConfig.fromEnv(Map.of("VULNSCAN_LLM_API_KEY", "  ")));

        assertTrue(e.getMessage().contains("VULNSCAN_LLM_API_KEY"), e.getMessage());
    }

    @Test
    void toStringMascaraAChave() {
        ApiLlmConfig config = ApiLlmConfig.fromEnv(Map.of("VULNSCAN_LLM_API_KEY", KEY));

        assertFalse(config.toString().contains(KEY), config.toString());
        assertTrue(config.toString().contains("nemotron"), config.toString());
    }
}
