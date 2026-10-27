package br.ufv.vulnscan.adapter.report;

import br.ufv.vulnscan.domain.model.Evidence;
import br.ufv.vulnscan.domain.model.Finding;
import br.ufv.vulnscan.domain.model.HttpRequest;
import br.ufv.vulnscan.domain.model.HttpResponse;
import br.ufv.vulnscan.domain.model.Payload;
import br.ufv.vulnscan.domain.model.ScanReport;
import br.ufv.vulnscan.domain.model.Severity;
import br.ufv.vulnscan.domain.model.Target;
import br.ufv.vulnscan.domain.model.Verdict;
import br.ufv.vulnscan.domain.model.VulnType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Testes do adaptador de relatório Markdown, gravando em diretório temporário. */
class MarkdownReportWriterTest {

    private static final Target TARGET = new Target("juice-shop", URI.create("http://localhost:3000"));
    private static final Instant STARTED = Instant.parse("2026-10-27T12:00:00Z");
    private static final Instant FINISHED = Instant.parse("2026-10-27T12:00:03.500Z");

    private final MarkdownReportWriter writer = new MarkdownReportWriter();

    @TempDir
    Path dir;

    @Test
    void formatoEMd() {
        assertEquals("md", writer.format());
    }

    @Test
    void gravaCabecalhoComAlvoPeriodoEDuracao() throws IOException {
        String md = render(report(List.of()));

        assertContains(md, "# Relatório de scan — juice-shop");
        assertContains(md, "- **Alvo:** juice-shop (http://localhost:3000)");
        assertContains(md, "- **Início:** 2026-10-27T12:00:00Z");
        assertContains(md, "- **Fim:** 2026-10-27T12:00:03.500Z");
        assertContains(md, "- **Duração:** 3500 ms");
        assertContains(md, "- **Achados:** 0");
    }

    @Test
    void semAchadosDizNenhumAchado() throws IOException {
        String md = render(report(List.of()));

        assertContains(md, "## Achados");
        assertContains(md, "Nenhum achado.");
    }

    @Test
    void gravaAchadoComVeredito() throws IOException {
        String md = render(report(List.of(finding("erro de sintaxe SQL do MySQL no corpo"))));

        assertContains(md, "- **Achados:** 1");
        assertContains(md, "### 1. SQL_INJECTION — HIGH");
        assertContains(md, "- **Veredito:** vulnerável (confiança 0.92, detector `heuristic`)");
        assertContains(md, "- **Justificativa:** erro de sintaxe SQL do MySQL no corpo");
        assertFalse(md.contains("Nenhum achado."), md);
    }

    @Test
    void variosAchadosNumeradosNaOrdem() throws IOException {
        Finding first = finding("primeiro");
        Finding second = new Finding(VulnType.XSS_REFLECTED, Severity.MEDIUM, TARGET, first.evidence(),
                new Verdict(false, 0.1, "segundo", "llm"));

        String md = render(report(List.of(first, second)));

        int one = md.indexOf("### 1. SQL_INJECTION — HIGH");
        int two = md.indexOf("### 2. XSS_REFLECTED — MEDIUM");
        assertTrue(one >= 0 && two > one, md);
        assertContains(md, "- **Veredito:** não vulnerável (confiança 0.10, detector `llm`)");
    }

    @Test
    void justificativaEmUmaLinha() throws IOException {
        String md = render(report(List.of(finding("linha um\nlinha dois\r\nlinha três"))));

        assertContains(md, "- **Justificativa:** linha um linha dois linha três\n");
    }

    @Test
    void gravaPayloadRequisicaoEResposta() throws IOException {
        String md = render(report(List.of(finding("motivo"))));

        assertContains(md, "- **Payload:** `sqli-quote` — aspa simples para quebrar a query\n\n```text\n'\n```\n");
        assertContains(md, "**Requisição**\n\n```http\n"
                + "POST http://localhost:3000/rest/user/login\n"
                + "content-type: application/json\n"
                + "\n"
                + "{\"email\":\"'\",\"password\":\"x\"}\n"
                + "```\n");
        assertContains(md, "**Resposta** (500, 42 ms)\n\n```http\n"
                + "content-type: text/html\n"
                + "\n"
                + "You have an error in your SQL syntax\n"
                + "```\n");
    }

    @Test
    void headersOrdenadosPorNome() throws IOException {
        Finding base = finding("motivo");
        HttpResponse response = new HttpResponse(200,
                Map.of("x-zeta", "z", "content-type", "text/html", "set-cookie", "a=1"), "", 5);
        Finding finding = finding(base.evidence().payload(), base.evidence().request(), response, "motivo");

        String md = render(report(List.of(finding)));

        assertContains(md, "content-type: text/html\nset-cookie: a=1\nx-zeta: z\n");
    }

    @Test
    void requisicaoSemCorpoNemHeaders() throws IOException {
        Finding base = finding("motivo");
        HttpRequest request = new HttpRequest("GET", URI.create("http://localhost:3000/"), null, null);
        Finding finding = finding(base.evidence().payload(), request, base.evidence().response(), "motivo");

        String md = render(report(List.of(finding)));

        assertContains(md, "**Requisição**\n\n```http\nGET http://localhost:3000/\n```\n");
    }

    @Test
    void corpoGrandeTruncado() throws IOException {
        Finding base = finding("motivo");
        String body = "a".repeat(2000) + "FIM-NAO-DEVE-APARECER" + "b".repeat(979);
        HttpResponse response = new HttpResponse(200, Map.of(), body, 5);
        Finding finding = finding(base.evidence().payload(), base.evidence().request(), response, "motivo");

        String md = render(report(List.of(finding)));

        assertContains(md, "a".repeat(2000) + "\n… (truncado: 3000 caracteres no total)\n");
        assertFalse(md.contains("FIM-NAO-DEVE-APARECER"), "corpo não foi truncado");
    }

    @Test
    void cercaMaiorQueCrasesDoConteudo() throws IOException {
        Finding base = finding("motivo");
        Payload payload = new Payload("xss-fence", VulnType.XSS_REFLECTED, "```<script>alert(1)</script>",
                "payload com crases");
        Finding finding = finding(payload, base.evidence().request(), base.evidence().response(), "motivo");

        String md = render(report(List.of(finding)));

        assertContains(md, "````text\n```<script>alert(1)</script>\n````\n");
    }

    @Test
    void criaDiretorioPaiESobrescreve() throws IOException {
        Path output = dir.resolve("saida/aninhada/report.md");

        writer.write(report(List.of()), output);
        writer.write(report(List.of(finding("ação com acentuação"))), output);

        String md = Files.readString(output, StandardCharsets.UTF_8);
        assertContains(md, "- **Achados:** 1");
        assertContains(md, "ação com acentuação");
    }

    @Test
    void saidaQueEDiretorioLancaUncheckedIOException() {
        assertThrows(UncheckedIOException.class, () -> writer.write(report(List.of()), dir));
    }

    private String render(ScanReport report) throws IOException {
        Path output = dir.resolve("report.md");
        writer.write(report, output);
        return Files.readString(output, StandardCharsets.UTF_8);
    }

    private static ScanReport report(List<Finding> findings) {
        return new ScanReport(TARGET, STARTED, FINISHED, findings);
    }

    private static Finding finding(String rationale) {
        Payload payload = new Payload("sqli-quote", VulnType.SQL_INJECTION, "'",
                "aspa simples para quebrar a query");
        HttpRequest request = new HttpRequest("POST", URI.create("http://localhost:3000/rest/user/login"),
                Map.of("content-type", "application/json"), "{\"email\":\"'\",\"password\":\"x\"}");
        HttpResponse response = new HttpResponse(500, Map.of("content-type", "text/html"),
                "You have an error in your SQL syntax", 42);
        return finding(payload, request, response, rationale);
    }

    private static Finding finding(Payload payload, HttpRequest request, HttpResponse response, String rationale) {
        return new Finding(payload.type(), Severity.HIGH, TARGET, new Evidence(payload, request, response),
                new Verdict(true, 0.92, rationale, "heuristic"));
    }

    private static void assertContains(String md, String expected) {
        assertTrue(md.contains(expected), () -> "esperado:\n" + expected + "\n\nrelatório:\n" + md);
    }
}
