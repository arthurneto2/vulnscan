package br.ufv.vulnscan.domain.detection;

import br.ufv.vulnscan.domain.model.Evidence;
import br.ufv.vulnscan.domain.model.HttpRequest;
import br.ufv.vulnscan.domain.model.HttpResponse;
import br.ufv.vulnscan.domain.model.Verdict;
import br.ufv.vulnscan.domain.model.VulnType;
import br.ufv.vulnscan.port.LlmClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Domínio: implementação de IA de {@link Detector}. Monta um prompt a partir da {@link Evidence},
 * pede um veredito à porta {@link LlmClient} e faz parse defensivo do texto para um
 * {@link Verdict}. É a linha que o experimento compara com o {@code HeuristicDetector}.
 *
 * <p>Regra dura de {@code docs/llm-contract.md}: {@link #judge} <b>nunca lança exceção</b> por
 * causa do modelo. Resposta não parseável → {@code Verdict(false, 0.0, "resposta do LLM não
 * parseável", "llm")}; falha do cliente (timeout/indisponível) → {@code Verdict(false, 0.0,
 * "LLM indisponível: ...", "llm")}.
 *
 * <p>O prompt (em inglês) pede o trecho de evidência e a justificativa <b>antes</b> do veredito,
 * traz um critério de evidência por tipo e avisa que "não vulnerável" é resposta comum (issue #61:
 * com o veredito primeiro, o modelo dizia "vulnerável" em todos os casos). <b>Grounding:</b> um
 * {@code vulnerable: true} cujo {@code evidence} esteja vazio ou não apareça no corpo (sem espaços,
 * sem diferenciar maiúsculas, sem desfazer escape de HTML; se o trecho for JSON, vale a string
 * desembrulhada ou todos os pares {@code "chave":valor} escalares citados) vira {@code Verdict(false, 0.2,
 * "veredito do LLM sem evidência na resposta: <rationale>", "llm")}. A regra é genérica, igual
 * para todo tipo — regras por tipo são do {@code HeuristicDetector}. Faz o parse com Jackson (exceção à convenção informal
 * "domínio só JDK"; o {@code DependencyRuleChecker} só proíbe pacotes do projeto, não libs).
 */
public final class LlmDetector implements Detector {

    private static final String NAME = "llm";
    private static final int MAX_BODY_CHARS = 4096;
    /** Trecho curto: citar um JSON longo faz o modelo entrar em repetição (Ollama aborta). */
    private static final int MAX_EVIDENCE_CHARS = 120;
    private static final String UNPARSEABLE = "resposta do LLM não parseável";
    private static final String UNGROUNDED = "veredito do LLM sem evidência na resposta";
    private static final double UNGROUNDED_CONFIDENCE = 0.2;

    private final ObjectMapper mapper = new ObjectMapper();
    private final LlmClient llm;

    public LlmDetector(LlmClient llm) {
        this.llm = Objects.requireNonNull(llm, "llm");
    }

    @Override
    public Verdict judge(Evidence evidence) {
        Objects.requireNonNull(evidence, "evidence");
        String raw;
        try {
            raw = llm.complete(prompt(evidence));
        } catch (RuntimeException e) {
            return new Verdict(false, 0.0, "LLM indisponível: " + e.getMessage(), NAME);
        }
        return ground(parse(raw), evidence);
    }

    /**
     * Grounding: um veredito positivo só vale se o trecho citado em {@code evidence} existir no corpo
     * da resposta (ignorando espaços e maiúsculas, sem desfazer escape de HTML). Regra genérica, igual
     * para todo tipo; vereditos negativos passam sem alteração.
     */
    private Verdict ground(Parsed parsed, Evidence evidence) {
        Verdict verdict = parsed.verdict();
        if (!verdict.vulnerable()) {
            return verdict;
        }
        String body = normalize(Objects.requireNonNullElse(evidence.response().body(), ""));
        if (grounded(parsed.evidence(), body)) {
            return verdict;
        }
        return new Verdict(false, UNGROUNDED_CONFIDENCE, UNGROUNDED + ": " + verdict.rationale(), NAME);
    }

    /**
     * O trecho está no corpo (já normalizado)? Literal primeiro. Se não, e o trecho for JSON (o modelo
     * resume respostas JSON em vez de copiar): string → o texto desembrulhado; objeto/array → todo par
     * {@code "chave":valor} escalar citado (em qualquer nível) está no corpo, e há ao menos um.
     */
    private boolean grounded(String quote, String body) {
        String literal = normalize(quote);
        if (literal.isEmpty()) {
            return false;
        }
        if (body.contains(literal)) {
            return true;
        }
        JsonNode node;
        try {
            node = mapper.readTree(quote);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return false;
        }
        if (node == null) {
            return false;
        }
        if (node.isTextual()) {
            String text = normalize(node.asText());
            return !text.isEmpty() && body.contains(text);
        }
        List<String> pairs = new ArrayList<>();
        collectScalarPairs(node, pairs);
        return !pairs.isEmpty() && pairs.stream().allMatch(pair -> body.contains(normalize(pair)));
    }

    /** Pares {@code "chave":valor} com valor escalar, em qualquer profundidade de objetos e arrays. */
    private void collectScalarPairs(JsonNode node, List<String> pairs) {
        if (node.isArray()) {
            node.forEach(child -> collectScalarPairs(child, pairs));
        } else if (node.isObject()) {
            node.properties().forEach(field -> {
                if (field.getValue().isValueNode()) {
                    pairs.add(jsonString(field.getKey()) + ":" + field.getValue().toString());
                } else {
                    collectScalarPairs(field.getValue(), pairs);
                }
            });
        }
    }

    private String jsonString(String text) {
        return mapper.getNodeFactory().textNode(text).toString();
    }

    /** Remove todo espaço em branco (o modelo reformata JSON/HTML) e passa para minúsculas. */
    private static String normalize(String text) {
        return text.replaceAll("\\s+", "").toLowerCase(java.util.Locale.ROOT);
    }

    /** Veredito parseado + o trecho citado pelo modelo ({@code ""} se ausente). */
    private record Parsed(Verdict verdict, String evidence) {}

    @Override
    public String name() {
        return NAME;
    }

    private Parsed parse(String raw) {
        Parsed unparseable = new Parsed(new Verdict(false, 0.0, UNPARSEABLE, NAME), "");
        String block = firstJsonBlock(raw);
        if (block == null) {
            return unparseable;
        }
        try {
            JsonNode node = mapper.readTree(block);
            if (!node.path("vulnerable").isBoolean()) {
                return unparseable;
            }
            boolean vulnerable = node.get("vulnerable").asBoolean();
            double confidence = clamp(node.path("confidence").asDouble(0.0));
            String rationale = node.path("rationale").asText("");
            return new Parsed(new Verdict(vulnerable, confidence, rationale, NAME),
                    node.path("evidence").asText(""));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return unparseable;
        }
    }

    /** Primeiro bloco {@code {...}} balanceado do texto, ou null se não houver. */
    private static String firstJsonBlock(String text) {
        if (text == null) {
            return null;
        }
        int start = text.indexOf('{');
        if (start < 0) {
            return null;
        }
        int depth = 0;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return text.substring(start, i + 1);
                }
            }
        }
        return null;
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private String prompt(Evidence evidence) {
        HttpRequest request = evidence.request();
        HttpResponse response = evidence.response();
        String body = Objects.requireNonNullElse(response.body(), "");
        String bodyForPrompt = body.length() > MAX_BODY_CHARS
                ? body.substring(0, MAX_BODY_CHARS) + "… [truncado]"
                : body;
        return """
                You are a careful web security analyst reviewing the result of ONE automated test.
                Most tests are expected to be NOT vulnerable: answering "vulnerable": false is common \
                and correct when the response does not contain clear evidence. Do not conclude that \
                the target is vulnerable just because a payload was sent or because of the test name.

                Vulnerability type under test: %s
                Criterion: %s

                Payload sent: %s
                Test description: %s

                Request: %s %s
                Request headers: %s
                Response status: %d
                Response time: %d ms
                Response headers: %s
                Response body:
                %s

                Answer with JSON only, with the fields in this exact order:
                {"evidence": "<a short excerpt (at most %d characters) copied character-for-character \
                from the Response body above, or an empty string if nothing in the body supports a \
                vulnerability>",
                 "rationale": "<one or two sentences applying the criterion to the evidence>",
                 "vulnerable": <true or false>,
                 "confidence": <number between 0 and 1>}
                """.formatted(evidence.payload().type(), criterion(evidence.payload().type()),
                evidence.payload().value(), evidence.payload().description(), request.method(), request.url(), request.headers(),
                response.status(), response.elapsedMillis(), response.headers(), bodyForPrompt,
                MAX_EVIDENCE_CHARS);
    }

    /**
     * Critério de evidência do tipo, em texto, para o modelo julgar. É contexto do prompt, não
     * regra aplicada pelo código (as regras por tipo são do {@code HeuristicDetector}).
     */
    private static String criterion(VulnType type) {
        return switch (type) {
            case SQL_INJECTION -> "Vulnerable only if the response body shows a database error message "
                    + "caused by the payload, or clear evidence that the query logic changed (for example "
                    + "a response time matching a sleep payload). A normal page or an empty result is NOT "
                    + "evidence.";
            case XSS_REFLECTED, XSS_STORED -> "Compare the payload with the body character by character. "
                    + "If the body contains HTML entities such as &lt; and &gt; where the payload had < and "
                    + ">, the input was escaped: the browser shows it as harmless text, so the answer is NOT "
                    + "vulnerable. Vulnerable only if the literal characters < and > of the payload appear "
                    + "in an HTML response.";
            case BROKEN_AUTH -> "The request was deliberately sent without valid authorization (see the "
                    + "test description and the request headers). Vulnerable only if the server granted "
                    + "access: a 2xx status returning protected data. 401, 403, a redirect to a login page "
                    + "or 404 means access was denied: NOT vulnerable.";
            case BROKEN_ACCESS_CONTROL -> "The request was deliberately sent with the token of a user who "
                    + "should NOT have access to this resource (see the test description). Vulnerable only "
                    + "if the server granted access: a 2xx status returning the protected data. 401, 403, "
                    + "a redirect to a login page or 404 means access was denied: NOT vulnerable.";
        };
    }
}
