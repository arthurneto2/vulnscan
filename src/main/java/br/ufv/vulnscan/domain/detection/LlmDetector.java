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
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

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
 * {@code vulnerable: true} cujo {@code evidence} esteja vazio ou não apareça no corpo nem nos diálogos capturados pelo navegador (#81) (sem espaços,
 * sem diferenciar maiúsculas, sem desfazer escape de HTML; se o trecho for JSON, vale a string
 * desembrulhada ou todos os pares {@code "chave":valor} escalares citados) vira {@code Verdict(false, 0.2,
 * "veredito do LLM sem evidência na resposta: <rationale>", "llm")}. A regra é genérica, igual
 * para todo tipo — regras por tipo são do {@code HeuristicDetector}. Faz o parse com Jackson (exceção à convenção informal
 * "domínio só JDK"; o {@code DependencyRuleChecker} só proíbe pacotes do projeto, não libs).
 */
public final class LlmDetector implements Detector {

    private static final String NAME = "llm";
    /** Prefixo da justificativa quando o cliente falhou (o modelo não respondeu). */
    public static final String UNAVAILABLE = "LLM indisponível";
    private static final int MAX_BODY_CHARS = 4096;
    /**
     * Token do payload que serve de âncora no corpo: 6+ letras e dígitos, com pelo menos um de
     * cada (ex.: o marcador {@code vsx9f8e7d6c}). Exigir dígito evita ancorar em palavra comum
     * do payload (ex.: {@code configuration} de um path), que aparece em qualquer lugar.
     */
    private static final Pattern TOKEN = Pattern.compile("(?=[A-Za-z0-9]*[0-9])(?=[A-Za-z0-9]*[A-Za-z])[A-Za-z0-9]{6,}");
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
            return new Verdict(false, 0.0, UNAVAILABLE + ": " + e.getMessage(), NAME);
        }
        return ground(parse(raw), evidence);
    }

    /**
     * O veredito é de indisponibilidade (o cliente falhou e o modelo não respondeu)? Distingue isso
     * de um "não vulnerável" de verdade; o benchmark conta o caso como {@code ERRO} (#74).
     */
    public static boolean unavailable(Verdict verdict) {
        return NAME.equals(verdict.detector()) && verdict.rationale() != null
                && verdict.rationale().startsWith(UNAVAILABLE);
    }

    /**
     * Grounding: um veredito positivo só vale se o trecho citado em {@code evidence} existir no corpo
     * da resposta ou nos diálogos capturados pelo navegador (#81) (ignorando espaços e maiúsculas, sem desfazer escape de HTML). Regra genérica, igual
     * para todo tipo; vereditos negativos passam sem alteração.
     */
    private Verdict ground(Parsed parsed, Evidence evidence) {
        Verdict verdict = parsed.verdict();
        if (!verdict.vulnerable()) {
            return verdict;
        }
        String body = normalize(Objects.requireNonNullElse(evidence.response().body(), ""));
        String dialogs = normalize(evidence.response().headers().getOrDefault(HttpResponse.DIALOGS_HEADER, ""));
        if (grounded(parsed.evidence(), body) || (!dialogs.isEmpty() && grounded(parsed.evidence(), dialogs))) {
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

    /**
     * Primeiro bloco {@code {...}} balanceado do texto, ou null se não houver. Chaves dentro de
     * strings JSON não contam (o {@code evidence} costuma citar trechos de JSON com chave aberta).
     */
    private static String firstJsonBlock(String text) {
        if (text == null) {
            return null;
        }
        int start = text.indexOf('{');
        if (start < 0) {
            return null;
        }
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
            } else if (c == '"') {
                inString = true;
            } else if (c == '{') {
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
        String bodyForPrompt = bodyExcerpt(body, evidence.payload().value());
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
                %sResponse body:
                %s

                Answer with JSON only, with the fields in this exact order:
                {"evidence": "<a short excerpt (at most %d characters) copied character-for-character \
                from the Response body above or from the Browser dialogs line, or an empty string if \
                nothing supports a vulnerability>",
                 "rationale": "<one or two sentences applying the criterion to the evidence>",
                 "vulnerable": <true or false>,
                 "confidence": <number between 0 and 1>}
                """.formatted(evidence.payload().type(), criterion(evidence.payload().type()),
                evidence.payload().value(), evidence.payload().description(), request.method(), request.url(), request.headers(),
                response.status(), response.elapsedMillis(), response.headers(), dialogsLine(response),
                bodyForPrompt,
                MAX_EVIDENCE_CHARS);
    }

    /**
     * Linha dos diálogos capturados pelo navegador (#81), só se houve algum: sem ela o modelo não
     * sabe o que é o pseudo-header. Sem navegador (HTTP puro) não há linha: dizer "nenhum" enganaria.
     */
    private static String dialogsLine(HttpResponse response) {
        String dialogs = response.headers().get(HttpResponse.DIALOGS_HEADER);
        if (dialogs == null || dialogs.isBlank()) {
            return "";
        }
        return "Browser dialogs: " + dialogs + "\n(JavaScript alert/confirm/prompt dialogs that actually fired "
                + "while a real browser rendered this page, captured by the scanner)\n";
    }

    /**
     * Trecho do corpo que vai para o prompt (no máximo {@link #MAX_BODY_CHARS} caracteres).
     * Corpo pequeno vai inteiro. Corpo grande: uma janela que começa metade antes da primeira
     * ocorrência do payload (ajustada para não passar do fim do corpo); se ele não aparece literal (escapado, reescrito pelo navegador),
     * a âncora é o seu token mais longo com letra e dígito encontrado (o marcador). Sem âncora, o começo
     * do corpo. Regra genérica, igual para todo tipo: escolhe o que mostrar, não julga.
     */
    private static String bodyExcerpt(String body, String payload) {
        if (body.length() <= MAX_BODY_CHARS) {
            return body;
        }
        int anchor = anchor(body, payload);
        if (anchor < 0) {
            return body.substring(0, MAX_BODY_CHARS) + "… [truncado]";
        }
        int start = Math.max(0, Math.min(anchor - MAX_BODY_CHARS / 2, body.length() - MAX_BODY_CHARS));
        int end = Math.min(body.length(), start + MAX_BODY_CHARS);
        return (start > 0 ? "[… " + start + " caracteres omitidos …]\n" : "")
                + body.substring(start, end)
                + (end < body.length() ? "… [truncado]" : "");
    }

    /** Posição do payload no corpo: literal; senão, o {@link #TOKEN} mais longo achado; senão -1. */
    private static int anchor(String body, String payload) {
        int literal = body.indexOf(payload);
        if (literal >= 0) {
            return literal;
        }
        return TOKEN.matcher(payload).results()
                .map(MatchResult::group)
                .sorted(Comparator.comparingInt(String::length).reversed())
                .mapToInt(body::indexOf)
                .filter(i -> i >= 0)
                .findFirst()
                .orElse(-1);
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
                    + "in an HTML response. Exception: if a Browser dialogs line is present and a dialog "
                    + "text contains the payload's unique marker (the vsx... token), the payload executed "
                    + "in the browser: vulnerable, even if other copies of it in the body look encoded or "
                    + "escaped.";
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
