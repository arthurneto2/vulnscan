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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Adaptador de relatório: grava o ScanReport como uma página HTML5 autossuficiente (UTF-8,
 * CSS embutido). Todo texto vindo do alvo e do payload é escapado ({@code & < > " '}): os
 * corpos trazem payloads de XSS refletido, e sem escape um {@code <script>} injetaria a
 * própria página do relatório. Corpos truncados em {@value #MAX_BODY_CHARS} caracteres. Cria o
 * diretório pai, sobrescreve; erro de I/O vira {@link UncheckedIOException}.
 */
public final class HtmlReportWriter implements ReportWriter {

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
        return "html";
    }

    private static String render(ScanReport report) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n<html lang=\"pt-br\">\n<head>\n<meta charset=\"utf-8\">\n");
        html.append("<title>Relatório de scan — ").append(esc(report.target().name())).append("</title>\n");
        html.append("<style>body{font-family:system-ui,sans-serif;margin:2rem;color:#1a1a1a}")
                .append("h1,h2{border-bottom:1px solid #ddd;padding-bottom:.2rem}")
                .append(".finding{border:1px solid #ddd;border-radius:6px;padding:1rem;margin:1rem 0}")
                .append(".vuln{color:#b00}.safe{color:#070}")
                .append("pre{background:#f6f6f6;padding:.6rem;overflow:auto;white-space:pre-wrap;word-break:break-word}")
                .append("</style>\n</head>\n<body>\n");
        html.append("<h1>Relatório de scan — ").append(esc(report.target().name())).append("</h1>\n");
        html.append("<ul>\n");
        html.append("<li><strong>Alvo:</strong> ").append(esc(report.target().name()))
                .append(" (").append(esc(report.target().baseUrl().toString())).append(")</li>\n");
        html.append("<li><strong>Início:</strong> ").append(esc(report.startedAt().toString())).append("</li>\n");
        html.append("<li><strong>Fim:</strong> ").append(esc(report.finishedAt().toString())).append("</li>\n");
        html.append("<li><strong>Duração:</strong> ")
                .append(Duration.between(report.startedAt(), report.finishedAt()).toMillis()).append(" ms</li>\n");
        html.append("<li><strong>Achados:</strong> ").append(report.findings().size()).append("</li>\n</ul>\n");
        html.append("<h2>Achados</h2>\n");
        if (report.findings().isEmpty()) {
            html.append("<p>Nenhum achado.</p>\n");
        }
        for (int i = 0; i < report.findings().size(); i++) {
            appendFinding(html, i + 1, report.findings().get(i));
        }
        html.append("</body>\n</html>\n");
        return html.toString();
    }

    private static void appendFinding(StringBuilder html, int number, Finding finding) {
        Verdict verdict = finding.verdict();
        Evidence evidence = finding.evidence();
        Payload payload = evidence.payload();
        html.append("<div class=\"finding\">\n");
        html.append("<h3>").append(number).append(". ").append(esc(finding.type().name()))
                .append(" — ").append(esc(finding.severity().name())).append("</h3>\n");
        html.append("<p class=\"").append(verdict.vulnerable() ? "vuln" : "safe").append("\">")
                .append(verdict.vulnerable() ? "vulnerável" : "não vulnerável")
                .append(" (confiança ").append(String.format(Locale.ROOT, "%.2f", verdict.confidence()))
                .append(", detector ").append(esc(verdict.detector())).append(")</p>\n");
        html.append("<p><strong>Justificativa:</strong> ").append(esc(verdict.rationale())).append("</p>\n");
        html.append("<p><strong>Payload:</strong> ").append(esc(payload.id())).append(" — ")
                .append(esc(payload.description())).append("</p>\n");
        html.append("<pre>").append(esc(payload.value())).append("</pre>\n");

        HttpRequest request = evidence.request();
        html.append("<p><strong>Requisição</strong></p>\n<pre>")
                .append(esc(http(request.method() + " " + request.url(), request.headers(), request.body())))
                .append("</pre>\n");

        HttpResponse response = evidence.response();
        html.append("<p><strong>Resposta</strong> (").append(response.status()).append(", ")
                .append(response.elapsedMillis()).append(" ms)</p>\n<pre>")
                .append(esc(http(null, response.headers(), response.body()))).append("</pre>\n");
        html.append("</div>\n");
    }

    /** Troca HTTP como texto: linha inicial, headers ordenados, linha vazia e corpo truncado. */
    private static String http(String firstLine, Map<String, String> headers, String body) {
        List<String> lines = new java.util.ArrayList<>();
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
        return body.length() <= MAX_BODY_CHARS ? body
                : body.substring(0, MAX_BODY_CHARS) + "\n… (truncado: " + body.length() + " caracteres no total)";
    }

    /** Escapa os caracteres que têm significado em HTML, para o conteúdo do alvo não injetar a página. */
    private static String esc(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
