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
        Verdict v = detector("{\"evidence\":\"erro\",\"vulnerable\":true,\"confidence\":0.82,"
                + "\"rationale\":\"erro SQL\"}").judge(sqli());

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
        assertEquals(1.0, detector("{\"evidence\":\"erro\",\"vulnerable\":true,\"confidence\":9.9}")
                .judge(sqli()).confidence());
        assertEquals(0.0, detector("{\"evidence\":\"erro\",\"vulnerable\":true,\"confidence\":-5}")
                .judge(sqli()).confidence());
    }

    @Test
    void confidenceERationaleAusentesUsamPadrao() {
        Verdict v = detector("{\"evidence\":\"erro\",\"vulnerable\":true}").judge(sqli());

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

    @Test
    void promptTrazContextoCompleto() {
        CapturingClient cap = new CapturingClient();
        Payload payload = new Payload("js-users", VulnType.BROKEN_AUTH, "/api/Users",
                "probe de NONE (requisição sem token)");
        HttpRequest request = new HttpRequest("GET", URI.create("http://localhost:3000/api/Users"),
                Map.of("X-Probe", "sem-token"), null);
        HttpResponse response = new HttpResponse(401, Map.of("content-type", "text/html"),
                "<h2>401 UnauthorizedError</h2>", 5123);

        new LlmDetector(cap).judge(new Evidence(payload, request, response));

        String prompt = cap.prompt;
        assertTrue(prompt.contains("probe de NONE (requisição sem token)"), "descrição do payload");
        assertTrue(prompt.contains("X-Probe=sem-token"), "headers da requisição");
        assertTrue(prompt.contains("Response status: 401"), "status explícito");
        assertTrue(prompt.contains("5123 ms"), "tempo de resposta");
        assertTrue(prompt.contains("content-type=text/html"), "headers da resposta");
        assertTrue(prompt.contains("<h2>401 UnauthorizedError</h2>"), "corpo");
    }

    @Test
    void promptDeSqliTrazCriterioDeSqliENaoODeXss() {
        String prompt = promptFor(VulnType.SQL_INJECTION);

        assertTrue(prompt.contains("database error message"), prompt);
        assertFalse(prompt.contains("HTML entities"), "critério de XSS vazou para SQLi");
    }

    @Test
    void promptDeXssTrazCriterioDeXss() {
        assertTrue(promptFor(VulnType.XSS_REFLECTED).contains("HTML entities"));
        assertTrue(promptFor(VulnType.XSS_STORED).contains("HTML entities"));
    }

    @Test
    void promptDeAuthDizQueARequisicaoFoiIndevida() {
        assertTrue(promptFor(VulnType.BROKEN_AUTH).contains("without valid authorization"));
        assertTrue(promptFor(VulnType.BROKEN_ACCESS_CONTROL).contains("should NOT have access"));
    }

    @Test
    void promptDizQueNaoVulneravelEComumEPedeEvidenciaPrimeiro() {
        String prompt = promptFor(VulnType.SQL_INJECTION);

        assertTrue(prompt.contains("\"vulnerable\": false is common"), prompt);
        int evidence = prompt.indexOf("\"evidence\"");
        assertTrue(evidence >= 0, "pede o campo evidence");
        assertTrue(evidence < prompt.indexOf("\"rationale\""), "evidence antes de rationale");
        assertTrue(prompt.indexOf("\"rationale\"") < prompt.indexOf("\"vulnerable\": <"),
                "rationale antes de vulnerable");
    }

    @Test
    void vulneravelComEvidenciaNoCorpoEMantido() {
        Verdict v = detector("{\"evidence\":\"<pre>erro</pre>\",\"rationale\":\"erro SQL\","
                + "\"vulnerable\":true,\"confidence\":0.9}").judge(sqli());

        assertTrue(v.vulnerable());
        assertEquals(0.9, v.confidence());
        assertEquals("erro SQL", v.rationale());
    }

    @Test
    void evidenciaComDiferencaDeEspacoEMaiusculaContaComoPresente() {
        Verdict v = detector("{\"evidence\":\"  <PRE>\\n ERRO</pre> \",\"vulnerable\":true,\"confidence\":0.9}")
                .judge(sqliComCorpo("<pre>erro</pre>"));

        assertTrue(v.vulnerable());
    }

    @Test
    void vulneravelSemEvidenciaViraNaoVulneravel() {
        for (String reply : new String[] {
                "{\"vulnerable\":true,\"confidence\":0.9,\"rationale\":\"r\"}",
                "{\"evidence\":\"\",\"vulnerable\":true,\"confidence\":0.9,\"rationale\":\"r\"}",
                "{\"evidence\":\"   \",\"vulnerable\":true,\"confidence\":0.9,\"rationale\":\"r\"}"}) {
            Verdict v = detector(reply).judge(sqli());

            assertFalse(v.vulnerable(), reply);
            assertEquals(0.2, v.confidence(), reply);
            assertTrue(v.rationale().startsWith("veredito do LLM sem evidência na resposta"), v.rationale());
            assertTrue(v.rationale().endsWith(": r"), "mantém o rationale original: " + v.rationale());
            assertEquals("llm", v.detector());
        }
    }

    @Test
    void vulneravelComEvidenciaAusenteDoCorpoViraNaoVulneravel() {
        // caso real do Juice Shop (/redirect): o modelo citou o payload cru, o corpo tem a versão escapada
        Payload payload = new Payload("xss-script", VulnType.XSS_REFLECTED, "<script>alert('m')</script>", "d");
        HttpRequest request = new HttpRequest("GET", URI.create("http://localhost:3000/redirect"), Map.of(), null);
        HttpResponse response = new HttpResponse(406, Map.of(),
                "Unrecognized target URL: &lt;script&gt;alert(&#39;m&#39;)&lt;/script&gt;", 3);

        Verdict v = detector("{\"evidence\":\"<script>alert('m')</script>\",\"rationale\":\"sem escape\","
                + "\"vulnerable\":true,\"confidence\":1}").judge(new Evidence(payload, request, response));

        assertFalse(v.vulnerable());
        assertEquals(0.2, v.confidence());
    }

    @Test
    void naoVulneravelNaoEAlterado() {
        Verdict v = detector("{\"evidence\":\"inexistente\",\"rationale\":\"bloqueado\","
                + "\"vulnerable\":false,\"confidence\":0.95}").judge(sqli());

        assertFalse(v.vulnerable());
        assertEquals(0.95, v.confidence());
        assertEquals("bloqueado", v.rationale());
    }

    // Casos reais do checkpoint da #61: em JSON o modelo resume em vez de copiar.

    @Test
    void evidenciaJsonResumidaComParesDoCorpoContaComoPresente() {
        Verdict v = detector("{\"evidence\":\"{\\\"id\\\":12,\\\"UserId\\\":31}\",\"vulnerable\":true,"
                + "\"confidence\":1}").judge(authComCorpo("{\"status\":\"success\",\"data\":{\"id\":12,"
                + "\"coupon\":null,\"UserId\":31,\"Products\":[]}}"));

        assertTrue(v.vulnerable(), v.rationale());
    }

    @Test
    void evidenciaJsonComParInexistenteViraNaoVulneravel() {
        Verdict v = detector("{\"evidence\":\"{\\\"id\\\":12,\\\"UserId\\\":99}\",\"vulnerable\":true,"
                + "\"confidence\":1}").judge(authComCorpo("{\"data\":{\"id\":12,\"UserId\":31}}"));

        assertFalse(v.vulnerable());
    }

    @Test
    void evidenciaJsonSemParEscalarNaoConta() {
        for (String quote : new String[] {"{\\\"data\\\": [...]}", "{\\\"data\\\": []}"}) {
            Verdict v = detector("{\"evidence\":\"" + quote + "\",\"vulnerable\":true,\"confidence\":1}")
                    .judge(authComCorpo("{\"data\":[{\"id\":1}]}"));

            assertFalse(v.vulnerable(), quote);
        }
    }

    @Test
    void evidenciaEntreAspasEDesembrulhada() {
        Verdict v = detector("{\"evidence\":\"\\\"OWASP Juice Shop!\\\"\",\"vulnerable\":true,\"confidence\":0.9}")
                .judge(authComCorpo("{\"title\":\"Welcome to OWASP Juice Shop!\",\"x\":1}"));

        assertTrue(v.vulnerable(), v.rationale());
    }

    @Test
    void payloadCruEntreAspasContinuaRebaixadoSeOCorpoEscapou() {
        Verdict v = detector("{\"evidence\":\"\\\"<script>alert('m')</script>\\\"\",\"vulnerable\":true,"
                + "\"confidence\":1}").judge(authComCorpo("to: &lt;script&gt;alert(&#39;m&#39;)&lt;/script&gt;"));

        assertFalse(v.vulnerable());
    }

    private static Evidence authComCorpo(String body) {
        Payload payload = new Payload("idor", VulnType.BROKEN_ACCESS_CONTROL, "/rest/basket/2", "probe");
        HttpRequest request = new HttpRequest("GET", URI.create("http://localhost:3000/rest/basket/2"), Map.of(), null);
        return new Evidence(payload, request, new HttpResponse(200, Map.of(), body, 5));
    }

    private static Evidence sqliComCorpo(String body) {
        Payload payload = new Payload("sqli-quote", VulnType.SQL_INJECTION, "1'", "aspas");
        HttpRequest request = new HttpRequest("GET", URI.create("http://localhost:8080/vuln?id=1"), Map.of(), null);
        return new Evidence(payload, request, new HttpResponse(500, Map.of(), body, 5));
    }

    private static String promptFor(VulnType type) {
        CapturingClient cap = new CapturingClient();
        Payload payload = new Payload("p", type, "x", "d");
        HttpRequest request = new HttpRequest("GET", URI.create("http://localhost:3000/x"), Map.of(), null);
        new LlmDetector(cap).judge(new Evidence(payload, request, new HttpResponse(200, Map.of(), "ok", 1)));
        return cap.prompt;
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
