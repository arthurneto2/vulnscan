package br.ufv.vulnscan.domain.module;

import br.ufv.vulnscan.domain.model.Payload;
import br.ufv.vulnscan.domain.model.VulnType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Domínio: parser do arquivo de payloads (ex.: {@code payloads/sqli.txt}). Puro, sem I/O:
 * recebe o texto já lido (quem lê o recurso é o Container). Formato: uma linha por payload,
 * {@code id<TAB>valor<TAB>descrição}; linhas vazias e começadas por {@code #} são ignoradas.
 * O valor não é aparado (espaços fazem parte do payload).
 */
public final class PayloadFile {

    private static final String SEPARATOR = "\t";
    private static final int FIELDS = 3;

    private PayloadFile() {
    }

    /**
     * @return os payloads na ordem do arquivo, todos do tipo {@code type}
     * @throws IllegalArgumentException linha sem 3 campos não vazios ou id repetido (cita a linha)
     */
    public static List<Payload> parse(String content, VulnType type) {
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(type, "type");
        List<Payload> payloads = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        String[] lines = content.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            int number = i + 1;
            String[] fields = line.split(SEPARATOR, -1);
            if (fields.length != FIELDS || fields[0].isEmpty() || fields[1].isEmpty() || fields[2].isEmpty()) {
                throw new IllegalArgumentException("linha " + number
                        + ": esperado id<TAB>valor<TAB>descrição, com os três campos preenchidos");
            }
            if (!ids.add(fields[0])) {
                throw new IllegalArgumentException("linha " + number + ": id repetido: " + fields[0]);
            }
            payloads.add(new Payload(fields[0], type, fields[1], fields[2]));
        }
        return List.copyOf(payloads);
    }
}
