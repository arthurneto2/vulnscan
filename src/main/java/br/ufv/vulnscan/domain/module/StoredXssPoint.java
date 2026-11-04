package br.ufv.vulnscan.domain.module;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Domínio: ponto de XSS armazenado — {@code POST} de formulário em {@code submitPath} (campos
 * {@code fixedFields}, na ordem dada, mais o payload em {@code payloadField}) e depois a
 * página {@code viewPath}, onde o conteúdo gravado aparece. Caminhos relativos à URL base.
 * {@code fixedFields}: nulo vira vazio; cópia imutável que mantém a ordem.
 */
public record StoredXssPoint(String submitPath, Map<String, String> fixedFields, String payloadField,
                             String viewPath) {

    public StoredXssPoint {
        fixedFields = fixedFields == null ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(fixedFields));
    }
}
