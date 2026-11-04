package br.ufv.vulnscan.app.benchmark;

import br.ufv.vulnscan.domain.model.VulnType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Aplicação (experimento): gabarito do experimento carregado de {@code ground_truth.yaml}
 * (ver {@code docs/ground-truth.md}). Campos opcionais do caso ({@code security},
 * {@code authMode}, {@code notes}) vêm nulos quando ausentes.
 */
public record GroundTruth(List<TargetCases> targets) {

    /** Alvo e seus casos. */
    public record TargetCases(String name, URI baseUrl, List<Case> cases) {}

    /** Um caso do gabarito: a verdade conhecida de um ponto/tipo num alvo. */
    public record Case(String id, VulnType type, String location, String security, String authMode,
                       boolean vulnerable, String notes) {}

    /** Lê e valida o YAML. Erro de I/O → {@link UncheckedIOException}. */
    public static GroundTruth load(Path yaml) {
        JsonNode root;
        try {
            root = new YAMLMapper().readTree(Files.readString(yaml));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        List<TargetCases> targets = new ArrayList<>();
        for (JsonNode target : root.path("targets")) {
            List<Case> cases = new ArrayList<>();
            for (JsonNode c : target.path("cases")) {
                cases.add(new Case(
                        text(c, "id"),
                        VulnType.valueOf(text(c, "type")),
                        text(c, "location"),
                        text(c, "security"),
                        text(c, "auth_mode"),
                        c.path("vulnerable").asBoolean(),
                        text(c, "notes")));
            }
            targets.add(new TargetCases(text(target, "name"),
                    URI.create(text(target, "base_url")), List.copyOf(cases)));
        }
        return new GroundTruth(List.copyOf(targets));
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
