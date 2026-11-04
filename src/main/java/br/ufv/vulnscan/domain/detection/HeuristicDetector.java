package br.ufv.vulnscan.domain.detection;

import br.ufv.vulnscan.domain.model.Evidence;
import br.ufv.vulnscan.domain.model.HttpResponse;
import br.ufv.vulnscan.domain.model.Verdict;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Domínio: implementação heurística de {@link Detector} — regras e regex, pura, sem I/O e
 * determinística. É a linha de base que o experimento compara com o LlmDetector.
 *
 * <p>Regras de SQL injection, na ordem (a primeira que casar decide):
 * <ol>
 *   <li>assinatura de erro de banco no corpo da resposta (MySQL/MariaDB, SQLite,
 *       PostgreSQL, genéricas), sem diferenciar maiúsculas;</li>
 *   <li>atraso: payload temporal — por convenção, {@code Payload.id()} começa com
 *       {@value #TIME_PAYLOAD_PREFIX} — e resposta de {@value #TIME_THRESHOLD_MILLIS} ms
 *       ou mais.</li>
 * </ol>
 *
 * <p>Regras de XSS (refletido e armazenado), sobre o payload concreto (com o marcador único
 * que o módulo pôs), na ordem:
 * <ol>
 *   <li>módulo com navegador: o pseudo-header {@link HttpResponse#DIALOGS_HEADER} tem um
 *       diálogo cujo texto está no payload (o marcador) → vulnerável (o navegador executou);</li>
 *   <li>o corpo contém o payload literalmente (diferenciando maiúsculas) e o
 *       {@code content-type} é HTML ou ausente → vulnerável;</li>
 *   <li>contém o payload, mas a resposta não é HTML (ex.: JSON) → não vulnerável, com
 *       confiança baixa;</li>
 *   <li>o payload só aparece depois de desfazer o escape de HTML ({@code &lt; &gt; &quot;
 *       &#39; &#039; &#x27; &amp;}) → não vulnerável;</li>
 *   <li>senão (inclui reflexão parcial, ex.: {@code <script>} removido) → não refletido.</li>
 * </ol>
 * O contexto da injeção (atributo, script, comentário) e CSP não são analisados.
 *
 * <p>Autenticação/autorização ainda não têm regras (issue #15) e recebem veredito negativo
 * com confiança 0. Nunca lança exceção por causa do tipo.
 */
public final class HeuristicDetector implements Detector {

    private static final String NAME = "heuristic";
    private static final String TIME_PAYLOAD_PREFIX = "sqli-time-";
    private static final long TIME_THRESHOLD_MILLIS = 5000;

    private static final double SQL_ERROR_CONFIDENCE = 0.9;
    private static final double TIME_DELAY_CONFIDENCE = 0.8;
    private static final double NO_SIGNAL_CONFIDENCE = 0.6;
    private static final double NO_RULES_CONFIDENCE = 0.0;
    private static final double XSS_DIALOG_CONFIDENCE = 0.95;
    private static final double XSS_RAW_HTML_CONFIDENCE = 0.9;
    private static final double XSS_ESCAPED_CONFIDENCE = 0.8;
    private static final double XSS_NOT_REFLECTED_CONFIDENCE = 0.6;
    private static final double XSS_NOT_HTML_CONFIDENCE = 0.5;

    /** Assinaturas de erro de banco por SGBD, testadas na ordem. */
    private static final Map<String, Pattern> SQL_ERRORS = new LinkedHashMap<>();

    static {
        SQL_ERRORS.put("MySQL", anyOf("You have an error in your SQL syntax", "Warning: mysql_",
                "mysqli_", "MariaDB server version"));
        SQL_ERRORS.put("SQLite", anyOf("SQLITE_ERROR", "sqlite3.OperationalError", "unrecognized token"));
        SQL_ERRORS.put("PostgreSQL", anyOf("syntax error at or near", "PSQLException"));
        SQL_ERRORS.put("genérico", Pattern.compile("SQLSTATE\\[|ODBC .* Driver", Pattern.CASE_INSENSITIVE));
    }

    @Override
    public Verdict judge(Evidence evidence) {
        Objects.requireNonNull(evidence, "evidence");
        return switch (evidence.payload().type()) {
            case SQL_INJECTION -> judgeSqlInjection(evidence);
            case XSS_REFLECTED, XSS_STORED -> judgeXss(evidence);
            case BROKEN_AUTH, BROKEN_ACCESS_CONTROL -> judgeAuth(evidence);
        };
    }

    @Override
    public String name() {
        return NAME;
    }

    private static Verdict judgeSqlInjection(Evidence evidence) {
        String body = Objects.requireNonNullElse(evidence.response().body(), "");
        for (Map.Entry<String, Pattern> error : SQL_ERRORS.entrySet()) {
            if (error.getValue().matcher(body).find()) {
                return new Verdict(true, SQL_ERROR_CONFIDENCE,
                        "assinatura de erro de SQL na resposta: " + error.getKey(), NAME);
            }
        }
        long elapsed = evidence.response().elapsedMillis();
        if (evidence.payload().id().startsWith(TIME_PAYLOAD_PREFIX) && elapsed >= TIME_THRESHOLD_MILLIS) {
            return new Verdict(true, TIME_DELAY_CONFIDENCE, "resposta levou " + elapsed
                    + " ms com payload temporal (limiar " + TIME_THRESHOLD_MILLIS + " ms)", NAME);
        }
        return new Verdict(false, NO_SIGNAL_CONFIDENCE,
                "nenhuma assinatura de erro de SQL nem atraso temporal", NAME);
    }

    private static Verdict judgeAuth(Evidence evidence) {
        int status = evidence.response().status();
        if (status == 401 || status == 403) {
            return new Verdict(false, 0.85, "acesso negado como esperado (status " + status + ")", NAME);
        }
        if (status >= 200 && status < 300) {
            return new Verdict(true, 0.7,
                    "acesso concedido (status " + status + ") onde se esperava 401/403", NAME);
        }
        return new Verdict(false, 0.6,
                "status " + status + " inconclusivo para autenticação/autorização", NAME);
    }

    private static Verdict judgeXss(Evidence evidence) {
        String body = Objects.requireNonNullElse(evidence.response().body(), "");
        String value = evidence.payload().value();
        if (dialogWithMarker(evidence.response(), value)) {
            return new Verdict(true, XSS_DIALOG_CONFIDENCE,
                    "o navegador executou o payload: diálogo com o marcador", NAME);
        }
        if (body.contains(value)) {
            String contentType = evidence.response().headers().get("content-type");
            if (contentType == null || contentType.contains("html")) {
                return new Verdict(true, XSS_RAW_HTML_CONFIDENCE,
                        "payload refletido sem escape em resposta HTML", NAME);
            }
            return new Verdict(false, XSS_NOT_HTML_CONFIDENCE,
                    "payload refletido, mas a resposta é " + contentType + ", não HTML", NAME);
        }
        if (unescapeHtml(body).contains(value)) {
            return new Verdict(false, XSS_ESCAPED_CONFIDENCE, "payload refletido, mas com escape de HTML", NAME);
        }
        return new Verdict(false, XSS_NOT_REFLECTED_CONFIDENCE, "payload não refletido na resposta", NAME);
    }

    /** Algum diálogo (do pseudo-header, separado por ", ") tem texto contido no payload — o marcador. */
    private static boolean dialogWithMarker(HttpResponse response, String value) {
        String dialogs = response.headers().get(HttpResponse.DIALOGS_HEADER);
        if (dialogs == null) {
            return false;
        }
        for (String text : dialogs.split(", ")) {
            if (!text.isBlank() && value.contains(text)) {
                return true;
            }
        }
        return false;
    }

    /** Desfaz só as entidades de escape comuns; {@code &amp;} por último, para não decodificar duas vezes. */
    private static String unescapeHtml(String text) {
        return text.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                .replace("&#39;", "'").replace("&#039;", "'").replace("&#x27;", "'")
                .replace("&amp;", "&");
    }

    /** Casa qualquer um dos textos literais, sem diferenciar maiúsculas. */
    private static Pattern anyOf(String... literals) {
        StringBuilder regex = new StringBuilder();
        for (String literal : literals) {
            if (!regex.isEmpty()) {
                regex.append('|');
            }
            regex.append(Pattern.quote(literal));
        }
        return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE);
    }
}
