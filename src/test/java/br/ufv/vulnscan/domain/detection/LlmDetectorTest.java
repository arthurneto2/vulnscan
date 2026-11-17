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
    void falhaDoClienteEhIndisponivel() {
        LlmClient falho = prompt -> {
            throw new IllegalStateException("API de IA respondeu 429: rate limit");
        };

        assertTrue(LlmDetector.unavailable(new LlmDetector(falho).judge(sqli())));
    }

    @Test
    void respostaNaoParseavelNaoEhIndisponivel() {
        assertFalse(LlmDetector.unavailable(detector("desculpe, não sei responder").judge(sqli())));
    }

    @Test
    void vereditoNormalNaoEhIndisponivel() {
        assertFalse(LlmDetector.unavailable(detector("{\"vulnerable\":false,\"confidence\":0.9}").judge(sqli())));
    }

    @Test
    void outroDetectorNaoEhIndisponivel() {
        assertFalse(LlmDetector.unavailable(
                new Verdict(false, 0.0, LlmDetector.UNAVAILABLE + ": x", "heuristic")));
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
        assertFalse(prompt.contains("caracteres omitidos"), "sem âncora: começo do corpo, como antes");
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

    @Test
    void chaveDesbalanceadaDentroDeStringNaoQuebraOParse() {
        // caso real (qwen2.5:14b): o trecho citado abre um objeto JSON que não fecha dentro da string
        Verdict v = detector("{\"evidence\": \"status\\\":\\\"success\\\",\\\"data\\\":{\\\"id\\\":11\", "
                + "\"rationale\": \"dado protegido\", \"vulnerable\": true, \"confidence\": 1}")
                .judge(authComCorpo("{\"status\":\"success\",\"data\":{\"id\":11,\"UserId\":2}}"));

        assertTrue(v.vulnerable(), v.rationale());
        assertEquals("dado protegido", v.rationale());
    }

    @Test
    void chaveFechandoDentroDeStringNaoEncerraOBlocoAntes() {
        Verdict v = detector("Resposta: {\"evidence\": \"}\", \"rationale\": \"r\", \"vulnerable\": false, "
                + "\"confidence\": 0.7} fim").judge(sqli());

        assertFalse(v.vulnerable());
        assertEquals(0.7, v.confidence());
        assertEquals("r", v.rationale());
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

    // --- diálogos do navegador no prompt e no grounding (issue #81) ---

    @Test
    void promptMostraDialogosQuandoHouve() {
        CapturingClient cap = new CapturingClient();

        new LlmDetector(cap).judge(xssNoNavegador("<option value=\"%3Cscript%3E\">", "vsx1a2b3c4d"));

        assertTrue(cap.prompt.contains("Browser dialogs: vsx1a2b3c4d"), cap.prompt);
        assertTrue(cap.prompt.contains("actually fired"), cap.prompt);
    }

    @Test
    void promptSemDialogosNaoTemALinha() {
        assertFalse(promptFor(VulnType.XSS_REFLECTED).contains("Browser dialogs:"));
    }

    /** #83: sem diálogos, a instrução do evidence é a de antes da #81 (a nova derrubou o Qwen em API). */
    @Test
    void promptSemDialogosUsaInstrucaoDeEvidenciaAntiga() {
        String prompt = promptFor(VulnType.BROKEN_ACCESS_CONTROL);

        assertTrue(prompt.contains("copied character-for-character from the Response body above, or an "
                + "empty string if nothing in the body supports a vulnerability>"), prompt);
        assertFalse(prompt.contains("from the Browser dialogs line"), prompt);
    }

    @Test
    void promptComDialogosUsaInstrucaoDeEvidenciaComDialogos() {
        CapturingClient cap = new CapturingClient();

        new LlmDetector(cap).judge(xssNoNavegador("<option value=\"%3Cscript%3E\">", "vsx1a2b3c4d"));

        assertTrue(cap.prompt.contains("from the Response body above or from the Browser dialogs line, or an "
                + "empty string if nothing supports a vulnerability>"), cap.prompt);
    }

    @Test
    void criterioDeXssFalaDoDialogoComMarcador() {
        String xss = promptFor(VulnType.XSS_REFLECTED);
        String sqli = promptFor(VulnType.SQL_INJECTION);

        assertTrue(xss.contains("dialog") && xss.contains("marker"), xss);
        assertFalse(sqli.contains("marker"), sqli);
    }

    @Test
    void evidenciaCitadaDoDialogoPassaNoGrounding() {
        LlmDetector detector = detector("{\"evidence\":\"vsx1a2b3c4d\",\"rationale\":\"o alert disparou\","
                + "\"vulnerable\":true,\"confidence\":0.9}");

        Verdict v = detector.judge(xssNoNavegador("<option value=\"%3Cscript%3E\">", "vsx1a2b3c4d"));

        assertTrue(v.vulnerable(), v.rationale());
        assertEquals(0.9, v.confidence());
    }

    @Test
    void evidenciaForaDoCorpoEDosDialogosContinuaRebaixada() {
        LlmDetector detector = detector("{\"evidence\":\"outra coisa\",\"rationale\":\"r\","
                + "\"vulnerable\":true,\"confidence\":0.9}");

        Verdict v = detector.judge(xssNoNavegador("<option value=\"%3Cscript%3E\">", "vsx1a2b3c4d"));

        assertFalse(v.vulnerable());
        assertEquals(0.2, v.confidence());
        assertTrue(v.rationale().contains("sem evidência"), v.rationale());
    }

    /** Resposta sintética do navegador: DOM no corpo e, se houver, os diálogos no pseudo-header. */
    private static Evidence xssNoNavegador(String dom, String dialogs) {
        Payload payload = new Payload("xss-script", VulnType.XSS_REFLECTED,
                "<script>alert('vsx1a2b3c4d')</script>", "tag script");
        HttpRequest request = new HttpRequest("GET", URI.create("http://localhost:8080/x#?d=1"), Map.of(), null);
        Map<String, String> headers = dialogs == null ? Map.of("content-type", "text/html")
                : Map.of("content-type", "text/html", HttpResponse.DIALOGS_HEADER, dialogs);
        return new Evidence(payload, request, new HttpResponse(200, headers, dom, 5));
    }

    // --- janela do corpo centrada no payload (issue #72) ---

    @Test
    void corpoGrandeMostraJanelaDoPayload() {
        String value = "<img src=x onerror=alert('vsx1a2b3c4d')>";
        String body = "A".repeat(10_000) + "PERTO" + value + "B".repeat(10_000);

        String prompt = promptWithBody(VulnType.XSS_REFLECTED, value, body);

        assertTrue(prompt.contains("PERTO" + value), "trecho do payload no corpo");
        assertTrue(prompt.contains("caracteres omitidos"), "marca de corte no começo");
        assertTrue(prompt.length() < 10_000, "parte do corpo continua limitada: " + prompt.length());
    }

    @Test
    void corpoGrandeComPayloadEscapadoAchaPeloMarcador() {
        String value = "<script>alert('vsx9f8e7d6c')</script>";
        String escaped = "&lt;script&gt;alert('vsx9f8e7d6c')&lt;/script&gt;";
        String body = "A".repeat(10_000) + escaped + "B".repeat(10_000);

        String prompt = promptWithBody(VulnType.XSS_REFLECTED, value, body);

        assertTrue(prompt.contains(escaped), "trecho escapado no prompt");
    }

    @Test
    void palavraComumDoPayloadNaoDeslocaAJanela() {
        String value = "/rest/admin/application-configuration";
        String body = "{\"config\":" + "X".repeat(8_000) + "SSL configurations" + "Y".repeat(8_000);

        String prompt = promptWithBody(VulnType.BROKEN_AUTH, value, body);

        assertTrue(prompt.contains("{\"config\":"), "começo do corpo (a evidência) no prompt");
        assertFalse(prompt.contains("caracteres omitidos"), "janela não ancora em palavra comum");
    }

    @Test
    void payloadPertoDoFimUsaJanelaInteiraAteOFim() {
        String value = "<svg onload=alert('vsx55aa66bb')>";
        String body = "A".repeat(10_000) + value + "FIM";

        String prompt = promptWithBody(VulnType.XSS_REFLECTED, value, body);

        assertTrue(prompt.contains("[… " + (body.length() - 4096) + " caracteres omitidos …]"),
                "janela de 4096 que termina no fim do corpo");
        int fim = prompt.indexOf(value + "FIM");
        assertTrue(fim >= 0, "trecho final no prompt");
        assertFalse(prompt.substring(fim).contains("[truncado]"), "nada cortado depois do fim");
    }

    private static String promptWithBody(VulnType type, String payloadValue, String body) {
        CapturingClient cap = new CapturingClient();
        Payload payload = new Payload("p", type, payloadValue, "teste");
        HttpRequest request = new HttpRequest("GET", URI.create("http://localhost:3000/x"), Map.of(), null);
        new LlmDetector(cap).judge(new Evidence(payload, request, new HttpResponse(200, Map.of(), body, 5)));
        return cap.prompt;
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
