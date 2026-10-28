package br.ufv.vulnscan.domain.detection;

import br.ufv.vulnscan.domain.model.Evidence;
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
 * Os demais tipos ainda não têm regras (issues #13 e #15) e recebem veredito negativo com
 * confiança 0. Nunca lança exceção por causa do tipo.
 */
public final class HeuristicDetector implements Detector {

    private static final String NAME = "heuristic";
    private static final String TIME_PAYLOAD_PREFIX = "sqli-time-";
    private static final long TIME_THRESHOLD_MILLIS = 5000;

    private static final double SQL_ERROR_CONFIDENCE = 0.9;
    private static final double TIME_DELAY_CONFIDENCE = 0.8;
    private static final double NO_SIGNAL_CONFIDENCE = 0.6;
    private static final double NO_RULES_CONFIDENCE = 0.0;

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
            case XSS_REFLECTED, XSS_STORED, BROKEN_AUTH, BROKEN_ACCESS_CONTROL -> new Verdict(false,
                    NO_RULES_CONFIDENCE, "sem regras heurísticas para " + evidence.payload().type(), NAME);
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
