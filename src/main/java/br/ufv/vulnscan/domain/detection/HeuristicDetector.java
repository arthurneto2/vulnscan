package br.ufv.vulnscan.domain.detection;

import br.ufv.vulnscan.domain.model.Evidence;
import br.ufv.vulnscan.domain.model.Verdict;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

public final class HeuristicDetector implements Detector {

    private static final String NAME = "heuristic";

    /** Assinaturas de erro de banco por SGBD, testadas na ordem. */
    private static final Map<String, Pattern> SQL_ERRORS = new LinkedHashMap<>();

    static {
        SQL_ERRORS.put("MySQL", anyOf("You have an error in your SQL syntax", "Warning: mysql_",
                "mysqli_", "MariaDB server version"));
        SQL_ERRORS.put("SQLite", anyOf("SQLITE_ERROR", "sqlite3.OperationalError", "unrecognized token"));
        SQL_ERRORS.put("PostgreSQL", anyOf("syntax error at or near", "PSQLException"));
        SQL_ERRORS.put("genérico", Pattern.compile("SQLSTATE\\[|ODBC .* Driver", Pattern.CASE_INSENSITIVE));
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

    @Override
    public Verdict judge(Evidence evidence) {
        Objects.requireNonNull(evidence, "evidence");
        String body = Objects.requireNonNullElse(evidence.response().body(), "");
        for (Map.Entry<String, Pattern> error : SQL_ERRORS.entrySet()) {
            if (error.getValue().matcher(body).find()) {
                return new Verdict(true, 0.9,
                        "assinatura de erro de SQL na resposta: " + error.getKey(), NAME);
            }
        }
        return new Verdict(false, 0.6, "nenhuma assinatura de erro de SQL nem atraso temporal", NAME);
    }

    @Override
    public String name() {
        return NAME;
    }
}
