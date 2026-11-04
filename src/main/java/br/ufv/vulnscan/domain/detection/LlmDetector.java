package br.ufv.vulnscan.domain.detection;

import br.ufv.vulnscan.domain.model.Evidence;
import br.ufv.vulnscan.domain.model.HttpRequest;
import br.ufv.vulnscan.domain.model.HttpResponse;
import br.ufv.vulnscan.domain.model.Verdict;
import br.ufv.vulnscan.port.LlmClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Objects;

/**
 * Domínio: implementação de IA de {@link Detector}. Monta um prompt a partir da {@link Evidence},
 * pede um veredito à porta {@link LlmClient} e faz parse defensivo do texto para um
 * {@link Verdict}. É a linha que o experimento compara com o {@code HeuristicDetector}.
 *
 * <p>Regra dura de {@code docs/llm-contract.md}: {@link #judge} <b>nunca lança exceção</b> por
 * causa do modelo. Resposta não parseável → {@code Verdict(false, 0.0, "resposta do LLM não
 * parseável", "llm")}; falha do cliente (timeout/indisponível) → {@code Verdict(false, 0.0,
 * "LLM indisponível: ...", "llm")}. Faz o parse com Jackson (exceção à convenção informal
 * "domínio só JDK"; o {@code DependencyRuleChecker} só proíbe pacotes do projeto, não libs).
 */
public final class LlmDetector implements Detector {

    private static final String NAME = "llm";
    private static final int MAX_BODY_CHARS = 4096;
    private static final String UNPARSEABLE = "resposta do LLM não parseável";

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
        return parse(raw);
    }

    @Override
    public String name() {
        return NAME;
    }

    private Verdict parse(String raw) {
        String block = firstJsonBlock(raw);
        if (block == null) {
            return new Verdict(false, 0.0, UNPARSEABLE, NAME);
        }
        try {
            JsonNode node = mapper.readTree(block);
            if (!node.path("vulnerable").isBoolean()) {
                return new Verdict(false, 0.0, UNPARSEABLE, NAME);
            }
            boolean vulnerable = node.get("vulnerable").asBoolean();
            double confidence = clamp(node.path("confidence").asDouble(0.0));
            String rationale = node.path("rationale").asText("");
            return new Verdict(vulnerable, confidence, rationale, NAME);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return new Verdict(false, 0.0, UNPARSEABLE, NAME);
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
                Você é um analisador de segurança. Decida se a resposta HTTP indica uma \
                vulnerabilidade do tipo informado. Responda só com JSON:
                {"vulnerable": boolean, "confidence": number, "rationale": string}

                Tipo da falha: %s
                Payload aplicado: %s

                Requisição: %s %s
                Headers da resposta: %s
                Corpo da resposta (status %d):
                %s
                """.formatted(evidence.payload().type(), evidence.payload().value(),
                request.method(), request.url(), response.headers(), response.status(), bodyForPrompt);
    }
}
