package br.ufv.vulnscan.adapter.report;

import br.ufv.vulnscan.domain.model.Evidence;
import br.ufv.vulnscan.domain.model.Finding;
import br.ufv.vulnscan.domain.model.HttpRequest;
import br.ufv.vulnscan.domain.model.HttpResponse;
import br.ufv.vulnscan.domain.model.Payload;
import br.ufv.vulnscan.domain.model.ScanReport;
import br.ufv.vulnscan.domain.model.Verdict;
import br.ufv.vulnscan.port.ReportWriter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Adaptador de relatório: grava o ScanReport em Markdown (UTF-8) — alvo, período e cada
 * achado com veredito, payload, requisição e resposta. Corpos são truncados e headers
 * ordenados por nome. Erro de I/O vira {@link UncheckedIOException}.
 */
public final class MarkdownReportWriter implements ReportWriter {

    private static final int MAX_BODY_CHARS = 2000;

    @Override
    public void write(ScanReport report, Path output) {
        try {
            Path parent = output.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(output, render(report), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public String format() {
        return "md";
    }

    private static String render(ScanReport report) {
        StringBuilder md = new StringBuilder();
        md.append("# Relatório de scan — ").append(report.target().name()).append("\n\n");
        md.append("- **Alvo:** ").append(report.target().name())
                .append(" (").append(report.target().baseUrl()).append(")\n");
        md.append("- **Início:** ").append(report.startedAt()).append('\n');
        md.append("- **Fim:** ").append(report.finishedAt()).append('\n');
        md.append("- **Duração:** ")
                .append(Duration.between(report.startedAt(), report.finishedAt()).toMillis()).append(" ms\n");
        md.append("- **Achados:** ").append(report.findings().size()).append("\n\n");
        md.append("## Achados\n\n");
        if (report.findings().isEmpty()) {
            md.append("Nenhum achado.\n");
        }
        for (int i = 0; i < report.findings().size(); i++) {
            appendFinding(md, i + 1, report.findings().get(i));
        }
        return md.toString();
    }

    private static void appendFinding(StringBuilder md, int number, Finding finding) {
        Verdict verdict = finding.verdict();
        md.append("### ").append(number).append(". ").append(finding.type())
                .append(" — ").append(finding.severity()).append("\n\n");
        md.append("- **Veredito:** ").append(verdict.vulnerable() ? "vulnerável" : "não vulnerável")
                .append(" (confiança ").append(String.format(Locale.ROOT, "%.2f", verdict.confidence()))
                .append(", detector `").append(verdict.detector()).append("`)\n");
        md.append("- **Justificativa:** ").append(verdict.rationale().replaceAll("\\R", " ")).append('\n');

        Evidence evidence = finding.evidence();
        Payload payload = evidence.payload();
        md.append("- **Payload:** `").append(payload.id()).append("` — ").append(payload.description())
                .append("\n\n");
        appendBlock(md, "text", payload.value());

        HttpRequest request = evidence.request();
        md.append("**Requisição**\n\n");
        appendBlock(md, "http", http(request.method() + " " + request.url(), request.headers(), request.body()));

        HttpResponse response = evidence.response();
        md.append("**Resposta** (").append(response.status()).append(", ")
                .append(response.elapsedMillis()).append(" ms)\n\n");
        appendBlock(md, "http", http(null, response.headers(), response.body()));
    }

    /** Monta uma troca HTTP como texto: linha inicial, headers ordenados, linha vazia e corpo. */
    private static String http(String firstLine, Map<String, String> headers, String body) {
        List<String> lines = new ArrayList<>();
        if (firstLine != null) {
            lines.add(firstLine);
        }
        if (headers != null) {
            new TreeMap<>(headers).forEach((name, value) -> lines.add(name + ": " + value));
        }
        if (body != null && !body.isEmpty()) {
            if (!lines.isEmpty()) {
                lines.add("");
            }
            lines.add(truncate(body));
        }
        return String.join("\n", lines);
    }

    private static String truncate(String body) {
        if (body.length() <= MAX_BODY_CHARS) {
            return body;
        }
        return body.substring(0, MAX_BODY_CHARS)
                + "\n… (truncado: " + body.length() + " caracteres no total)";
    }

    private static void appendBlock(StringBuilder md, String language, String content) {
        String fence = fence(content);
        md.append(fence).append(language).append('\n').append(content).append('\n').append(fence).append("\n\n");
    }

    /** Cerca com mais crases que a maior sequência do conteúdo (mínimo 3), para não quebrar o bloco. */
    private static String fence(String content) {
        int longest = 0;
        int run = 0;
        for (int i = 0; i < content.length(); i++) {
            run = content.charAt(i) == '`' ? run + 1 : 0;
            longest = Math.max(longest, run);
        }
        return "`".repeat(Math.max(3, longest + 1));
    }
}
