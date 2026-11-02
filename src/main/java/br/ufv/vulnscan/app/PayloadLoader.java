package br.ufv.vulnscan.app;

import br.ufv.vulnscan.domain.model.Payload;
import br.ufv.vulnscan.domain.model.VulnType;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Aplicação: carrega payloads de um recurso de texto do classpath e os injeta nos módulos
 * (o domínio não toca disco). Formato por linha: {@code id<TAB>value<TAB>descrição}; linhas
 * em branco e as iniciadas por {@code #} são ignoradas. A descrição é opcional.
 */
public final class PayloadLoader {

    private PayloadLoader() {
    }

    /** Lê o recurso {@code resourcePath} do classpath (UTF-8) e faz o parse dos payloads. */
    public static List<Payload> load(String resourcePath, VulnType type) {
        Objects.requireNonNull(resourcePath, "resourcePath");
        ClassLoader loader = PayloadLoader.class.getClassLoader();
        try (InputStream in = loader.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalArgumentException("recurso de payloads não encontrado: " + resourcePath);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                return parse(reader.lines().toList(), type);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("falha ao ler payloads: " + resourcePath, e);
        }
    }

    /** Parse puro (sem I/O): transforma as linhas em payloads do {@code type} dado. */
    public static List<Payload> parse(List<String> lines, VulnType type) {
        Objects.requireNonNull(lines, "lines");
        Objects.requireNonNull(type, "type");
        List<Payload> payloads = new ArrayList<>();
        for (String line : lines) {
            if (line.isBlank() || line.stripLeading().startsWith("#")) {
                continue;
            }
            String[] fields = line.split("\t", -1);
            if (fields.length < 2 || fields[0].isBlank() || fields[1].isEmpty()) {
                throw new IllegalArgumentException("linha de payload inválida (esperado id<TAB>value[<TAB>descrição]): " + line);
            }
            String id = fields[0];
            String value = fields[1];
            String description = fields.length >= 3 ? fields[2] : "";
            payloads.add(new Payload(id, type, value, description));
        }
        return List.copyOf(payloads);
    }
}
