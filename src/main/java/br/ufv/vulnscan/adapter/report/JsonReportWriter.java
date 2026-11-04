package br.ufv.vulnscan.adapter.report;

import br.ufv.vulnscan.domain.model.Evidence;
import br.ufv.vulnscan.domain.model.Finding;
import br.ufv.vulnscan.domain.model.HttpRequest;
import br.ufv.vulnscan.domain.model.HttpResponse;
import br.ufv.vulnscan.domain.model.Payload;
import br.ufv.vulnscan.domain.model.ScanReport;
import br.ufv.vulnscan.domain.model.Target;
import br.ufv.vulnscan.domain.model.Verdict;
import br.ufv.vulnscan.port.ReportWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

/**
 * Adaptador de relatório: grava o ScanReport em JSON (UTF-8), para consumo por máquina. A
 * árvore é montada à mão (nomes de campo controlados, URI como string, corpos truncados em
 * {@value #MAX_BODY_CHARS} caracteres). Datas em ISO-8601 ({@code Instant.toString()}). Cria o
 * diretório pai, sobrescreve; erro de I/O vira {@link UncheckedIOException}.
 */
public final class JsonReportWriter implements ReportWriter {

    private static final int MAX_BODY_CHARS = 2000;

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void write(ScanReport report, Path output) {
        try {
            Path parent = output.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(output, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(tree(report)),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public String format() {
        return "json";
    }

    private ObjectNode tree(ScanReport report) {
        ObjectNode root = mapper.createObjectNode();
        root.set("target", target(report.target()));
        root.put("startedAt", report.startedAt().toString());
        root.put("finishedAt", report.finishedAt().toString());
        root.put("durationMillis", Duration.between(report.startedAt(), report.finishedAt()).toMillis());
        root.put("findingCount", report.findings().size());
        ArrayNode findings = root.putArray("findings");
        report.findings().forEach(f -> findings.add(finding(f)));
        return root;
    }

    private ObjectNode target(Target target) {
        ObjectNode node = mapper.createObjectNode();
        node.put("name", target.name());
        node.put("baseUrl", target.baseUrl().toString());
        node.set("headers", headers(target.headers()));
        return node;
    }

    private ObjectNode finding(Finding finding) {
        ObjectNode node = mapper.createObjectNode();
        node.put("type", finding.type().name());
        node.put("severity", finding.severity().name());
        node.set("verdict", verdict(finding.verdict()));
        Evidence evidence = finding.evidence();
        node.set("payload", payload(evidence.payload()));
        node.set("request", request(evidence.request()));
        node.set("response", response(evidence.response()));
        return node;
    }

    private ObjectNode verdict(Verdict verdict) {
        ObjectNode node = mapper.createObjectNode();
        node.put("vulnerable", verdict.vulnerable());
        node.put("confidence", verdict.confidence());
        node.put("rationale", verdict.rationale());
        node.put("detector", verdict.detector());
        return node;
    }

    private ObjectNode payload(Payload payload) {
        ObjectNode node = mapper.createObjectNode();
        node.put("id", payload.id());
        node.put("type", payload.type().name());
        node.put("value", payload.value());
        node.put("description", payload.description());
        return node;
    }

    private ObjectNode request(HttpRequest request) {
        ObjectNode node = mapper.createObjectNode();
        node.put("method", request.method());
        node.put("url", request.url().toString());
        node.set("headers", headers(request.headers()));
        node.put("body", truncate(request.body()));
        return node;
    }

    private ObjectNode response(HttpResponse response) {
        ObjectNode node = mapper.createObjectNode();
        node.put("status", response.status());
        node.set("headers", headers(response.headers()));
        node.put("body", truncate(response.body()));
        node.put("elapsedMillis", response.elapsedMillis());
        return node;
    }

    private ObjectNode headers(Map<String, String> headers) {
        ObjectNode node = mapper.createObjectNode();
        if (headers != null) {
            headers.forEach(node::put);
        }
        return node;
    }

    /** null → null (vira JSON null); corpo acima do limite é cortado com marca. */
    private static String truncate(String body) {
        if (body == null) {
            return null;
        }
        return body.length() > MAX_BODY_CHARS
                ? body.substring(0, MAX_BODY_CHARS) + "… [truncado] (" + body.length() + " caracteres no total)"
                : body;
    }
}
