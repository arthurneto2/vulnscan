package br.ufv.vulnscan.domain.detection;

import br.ufv.vulnscan.domain.model.Evidence;
import br.ufv.vulnscan.domain.model.HttpRequest;
import br.ufv.vulnscan.domain.model.HttpResponse;
import br.ufv.vulnscan.domain.model.Payload;
import br.ufv.vulnscan.domain.model.Verdict;
import br.ufv.vulnscan.domain.model.VulnType;
import br.ufv.vulnscan.port.LlmClient;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Testes do LlmDetector com LlmClient fake (texto fixo ou exceção), sem rede/Ollama. */
class LlmDetectorTest {

    @Test
    void nomeEllm() {
        assertEquals("llm", new LlmDetector(reply("{}")).name());
    }

    @Test
    void judgeNuloLancaNpe() {
        assertThrows(NullPointerException.class, () -> new LlmDetector(reply("{}")).judge(null));
    }

    @Test
    void jsonValidoViraVerdict() {
        Verdict v = detector("{\"vulnerable\":true,\"confidence\":0.82,\"rationale\":\"erro SQL\"}")
                .judge(sqli());

        assertTrue(v.vulnerable());
        assertEquals(0.82, v.confidence());
        assertEquals("erro SQL", v.rationale());
        assertEquals("llm", v.detector());
    }

    @Test
    void jsonCercadoDeTextoEExtraido() {
        Verdict v = detector("Claro! {\"vulnerable\": false, \"confidence\": 0.1, "
                + "\"rationale\": \"sem sinal\"} espero ter ajudado").judge(sqli());

        assertFalse(v.vulnerable());
        assertEquals(0.1, v.confidence());
        assertEquals("sem sinal", v.rationale());
    }

    @Test
    void lixoSemJsonNaoEParseavel() {
        Verdict v = detector("desculpe, não sei responder").judge(sqli());

        assertFalse(v.vulnerable());
        assertEquals(0.0, v.confidence());
        assertTrue(v.rationale().contains("não parseável"), v.rationale());
        assertEquals("llm", v.detector());
    }

    @Test
    void semCampoVulnerableNaoEParseavel() {
        Verdict v = detector("{\"confidence\":0.9,\"rationale\":\"x\"}").judge(sqli());

        assertFalse(v.vulnerable());
        assertTrue(v.rationale().contains("não parseável"), v.rationale());
    }

    @Test
    void confidenceForaDoIntervaloEGrampeada() {
        assertEquals(1.0, detector("{\"vulnerable\":true,\"confidence\":9.9}").judge(sqli()).confidence());
        assertEquals(0.0, detector("{\"vulnerable\":true,\"confidence\":-5}").judge(sqli()).confidence());
    }

    @Test
    void confidenceERationaleAusentesUsamPadrao() {
        Verdict v = detector("{\"vulnerable\":true}").judge(sqli());

        assertTrue(v.vulnerable());
        assertEquals(0.0, v.confidence());
        assertEquals("", v.rationale());
    }

    @Test
    void clienteQueLancaViraBaixaConfianca() {
        LlmClient falho = prompt -> {
            throw new java.io.UncheckedIOException(new java.io.IOException("timeout"));
        };
        Verdict v = new LlmDetector(falho).judge(sqli());

        assertFalse(v.vulnerable());
        assertEquals(0.0, v.confidence());
        assertTrue(v.rationale().contains("indisponível"), v.rationale());
        assertEquals("llm", v.detector());
    }

    @Test
    void promptCitaTipoPayloadRequisicaoETruncaCorpo() {
        StringBuilder corpo = new StringBuilder();
        for (int i = 0; i < 10_000; i++) {
            corpo.append('A');
        }
        CapturingClient cap = new CapturingClient();
        Payload payload = new Payload("sqli-quote", VulnType.SQL_INJECTION, "1'", "aspas");
        HttpRequest request = new HttpRequest("GET", URI.create("http://localhost:8080/vuln?id=1'"),
                Map.of(), null);
        Evidence evidence = new Evidence(payload, request, new HttpResponse(200, Map.of(), corpo.toString(), 5));

        new LlmDetector(cap).judge(evidence);

        String prompt = cap.prompt;
        assertTrue(prompt.contains("SQL_INJECTION"), prompt);
        assertTrue(prompt.contains("1'"), "payload");
        assertTrue(prompt.contains("GET"), "método");
        assertTrue(prompt.contains("http://localhost:8080/vuln?id=1'"), "url");
        assertTrue(prompt.contains("[truncado]"), "marca de truncamento");
        assertTrue(prompt.length() < corpo.length(), "corpo não foi mandado inteiro");
    }

    private static LlmDetector detector(String reply) {
        return new LlmDetector(reply(reply));
    }

    private static LlmClient reply(String text) {
        return prompt -> text;
    }

    private static Evidence sqli() {
        Payload payload = new Payload("sqli-quote", VulnType.SQL_INJECTION, "1'", "aspas");
        HttpRequest request = new HttpRequest("GET", URI.create("http://localhost:8080/vuln?id=1"), Map.of(), null);
        return new Evidence(payload, request, new HttpResponse(200, Map.of(), "<pre>erro</pre>", 5));
    }

    private static final class CapturingClient implements LlmClient {
        String prompt;

        @Override
        public String complete(String prompt) {
            this.prompt = prompt;
            return "{\"vulnerable\":false}";
        }
    }
}
