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
        SQL_ERRORS.put("MySQL", Pattern.compile("You have an error in your SQL syntax"));
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
