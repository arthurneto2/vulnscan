package br.ufv.vulnscan.app.benchmark;

import br.ufv.vulnscan.app.benchmark.CaseRunner.CaseResult;
import br.ufv.vulnscan.app.benchmark.GroundTruth.Case;
import br.ufv.vulnscan.app.benchmark.GroundTruth.TargetCases;
import br.ufv.vulnscan.domain.model.VulnType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Testes do BenchmarkRunner com CaseRunner fake e saída em @TempDir. */
class BenchmarkRunnerTest {

    @TempDir
    Path out;

    private static final URI BASE = URI.create("http://localhost:3000");
    // 4 casos: 2 SQLi (1 vulnerável, 1 não), 2 XSS (1 vulnerável, 1 não)
    private static final GroundTruth GT = new GroundTruth(List.of(new TargetCases("alvo", BASE, List.of(
            new Case("sqli-pos", VulnType.SQL_INJECTION, "/a", null, null, true, null),
            new Case("sqli-neg", VulnType.SQL_INJECTION, "/b", null, null, false, null),
            new Case("xss-pos", VulnType.XSS_REFLECTED, "/c", null, null, true, null),
            new Case("xss-neg", VulnType.XSS_REFLECTED, "/d", null, null, false, null)))));

    @Test
    void gravaUmCsvPorExecucaoEUmResumo() throws IOException {
        new BenchmarkRunner(GT, perfectRunner(), List.of("heuristic"), 3).run(out);

        assertTrue(Files.exists(out.resolve("run-1.csv")));
        assertTrue(Files.exists(out.resolve("run-2.csv")));
        assertTrue(Files.exists(out.resolve("run-3.csv")));
        assertTrue(Files.exists(out.resolve("summary.csv")));
        // cabeçalho + 4 casos × 1 detector
        assertEquals(5, Files.readAllLines(out.resolve("run-1.csv")).size());
    }

    @Test
    void detectorPerfeitoTemMetricas1() {
        Map<String, Map<String, ConfusionMatrix>> m =
                new BenchmarkRunner(GT, perfectRunner(), List.of("heuristic"), 1).run(out);

        ConfusionMatrix total = m.get("heuristic").get("TOTAL");
        assertEquals(2, total.tp());
        assertEquals(0, total.fp());
        assertEquals(2, total.tn());
        assertEquals(0, total.fn());
        assertEquals(1.0, total.precision(), 1e-9);
        assertEquals(1.0, total.recall(), 1e-9);
    }

    @Test
    void doisDetectoresNRunsContamTudo() {
        Map<String, Map<String, ConfusionMatrix>> m =
                new BenchmarkRunner(GT, alwaysVulnerable(), List.of("heuristic", "llm"), 2).run(out);

        // sempre-vulnerável: os 2 positivos viram TP e os 2 negativos viram FP, por run; 2 runs
        ConfusionMatrix total = m.get("llm").get("TOTAL");
        assertEquals(4, total.tp());
        assertEquals(4, total.fp());
        ConfusionMatrix porTipo = m.get("heuristic").get("SQL_INJECTION");
        assertEquals(2, porTipo.tp());
        assertEquals(2, porTipo.fp());
    }

    @Test
    void resumoTemLinhasPorTipoETotalPorDetector() throws IOException {
        new BenchmarkRunner(GT, perfectRunner(), List.of("heuristic", "llm"), 1).run(out);

        String summary = Files.readString(out.resolve("summary.csv"));
        assertTrue(summary.contains("heuristic"), summary);
        assertTrue(summary.contains("llm"), summary);
        assertTrue(summary.contains("SQL_INJECTION"), summary);
        assertTrue(summary.contains("XSS_REFLECTED"), summary);
        assertTrue(summary.contains("TOTAL"), summary);
    }

    @Test
    void erroDeUmCasoViraLinhaErroSemAbortar() throws IOException {
        CaseRunner comErro = (c, base, det) -> {
            if (c.id().equals("sqli-pos")) {
                throw new IllegalStateException("falhou");
            }
            return new CaseResult(false, 0.0, 1);
        };

        Map<String, Map<String, ConfusionMatrix>> m =
                new BenchmarkRunner(GT, comErro, List.of("heuristic"), 1).run(out);

        assertTrue(Files.readString(out.resolve("run-1.csv")).contains("ERRO"));
        // o caso que falhou não entra na matriz: sobra 1 FN? não — ele era positivo e deu erro, não conta
        ConfusionMatrix total = m.get("heuristic").get("TOTAL");
        assertEquals(0, total.tp());
        assertEquals(1, total.fn()); // xss-pos previsto false
    }

    @Test
    void resetaOLabAntesDeCadaExecucao() {
        List<String> log = new ArrayList<>();
        LabReset reset = urls -> log.add("reset " + urls);
        CaseRunner runner = (c, base, det) -> {
            log.add("caso " + c.id());
            return new CaseResult(c.vulnerable(), 0.9, 1);
        };

        new BenchmarkRunner(GT, runner, List.of("heuristic"), 3, reset).run(out);

        List<String> umaExecucao = List.of("reset [" + BASE + "]",
                "caso sqli-pos", "caso sqli-neg", "caso xss-pos", "caso xss-neg");
        List<String> esperado = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            esperado.addAll(umaExecucao);
        }
        assertEquals(esperado, log);
    }

    @Test
    void falhaNoResetAbortaOBenchmark() {
        List<String> casos = new ArrayList<>();
        LabReset falha = urls -> {
            throw new IllegalStateException("lab não subiu");
        };
        CaseRunner runner = (c, base, det) -> {
            casos.add(c.id());
            return new CaseResult(false, 0.0, 1);
        };

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new BenchmarkRunner(GT, runner, List.of("heuristic"), 2, falha).run(out));

        assertEquals("lab não subiu", e.getMessage());
        assertTrue(casos.isEmpty(), casos::toString);
    }

    @Test
    void esqueceAsSessoesDepoisDoReset() {
        List<String> log = new ArrayList<>();
        CaseRunner runner = new LoggingRunner(log);

        new BenchmarkRunner(GT, runner, List.of("heuristic"), 2, urls -> log.add("reset")).run(out);

        assertEquals(List.of("reset", "forget", "caso", "caso", "caso", "caso",
                "reset", "forget", "caso", "caso", "caso", "caso"), log);
    }

    @Test
    void semResetNaoEsqueceAsSessoes() {
        List<String> log = new ArrayList<>();

        new BenchmarkRunner(GT, new LoggingRunner(log), List.of("heuristic"), 2).run(out);

        assertTrue(log.stream().noneMatch("forget"::equals), log::toString);
    }

    /** Runner que registra os casos e o forgetSessions() num log compartilhado. */
    private record LoggingRunner(List<String> log) implements CaseRunner {
        @Override
        public CaseResult run(Case c, URI base, String det) {
            log.add("caso");
            return new CaseResult(c.vulnerable(), 0.9, 1);
        }

        @Override
        public void forgetSessions() {
            log.add("forget");
        }
    }

    @Test
    void erroGravaOMotivoSemVirgula() throws IOException {
        CaseRunner comErro = (c, base, det) -> {
            if (c.id().equals("sqli-pos")) {
                throw new IllegalStateException("falhou, de novo\nlinha 2");
            }
            return new CaseResult(c.vulnerable(), 0.9, 1);
        };

        new BenchmarkRunner(GT, comErro, List.of("heuristic"), 1).run(out);

        List<String> linhas = Files.readAllLines(out.resolve("run-1.csv"));
        assertTrue(linhas.get(0).endsWith(",tempo_ms,erro"), linhas.get(0));
        assertEquals("1,sqli-pos,SQL_INJECTION,heuristic,true,,,ERRO,,falhou; de novo linha 2", linhas.get(1));
        assertTrue(linhas.get(2).endsWith(",TN,1,"), linhas.get(2));
        assertEquals(10, linhas.get(2).split(",", -1).length, linhas.get(2));
    }

    @Test
    void motivoDoErroLongoECortado() throws IOException {
        CaseRunner comErro = (c, base, det) -> {
            throw new IllegalStateException("x".repeat(500));
        };

        new BenchmarkRunner(GT, comErro, List.of("heuristic"), 1).run(out);

        String linha = Files.readAllLines(out.resolve("run-1.csv")).get(1);
        assertEquals(200, linha.substring(linha.lastIndexOf(',') + 1).length(), linha);
    }

    @Test
    void resumoContaErrosPorTipoETotal() throws IOException {
        // o llm falha em todo caso de XSS; a heurística acerta tudo
        CaseRunner runner = (c, base, det) -> {
            if (det.equals("llm") && c.type() == VulnType.XSS_REFLECTED) {
                throw new IllegalStateException("LLM indisponível em 1 de 1 chamadas");
            }
            return new CaseResult(c.vulnerable(), 0.9, 1);
        };

        new BenchmarkRunner(GT, runner, List.of("heuristic", "llm"), 1).run(out);

        List<String> resumo = Files.readAllLines(out.resolve("summary.csv"));
        assertTrue(resumo.get(0).endsWith(",tempo_medio_ms,erros"), resumo.get(0));
        assertEquals("0", erros(resumo, "heuristic", "TOTAL"));
        assertEquals("0", erros(resumo, "llm", "SQL_INJECTION"));
        assertEquals("2", erros(resumo, "llm", "TOTAL"));
        // tipo só com ERRO aparece com a matriz zerada
        assertTrue(resumo.contains("llm,XSS_REFLECTED,0,0,0,0,0.0000,0.0000,0.0000,0.0000,1.0000,2"),
                String.join("\n", resumo));
    }

    private static String erros(List<String> resumo, String detector, String tipo) {
        String linha = resumo.stream().filter(l -> l.startsWith(detector + "," + tipo + ","))
                .findFirst().orElseThrow(() -> new AssertionError("sem " + detector + "/" + tipo + ": " + resumo));
        return linha.substring(linha.lastIndexOf(',') + 1);
    }

    /** Runner perfeito: prevê exatamente o gabarito. */
    private static CaseRunner perfectRunner() {
        return (c, base, det) -> new CaseResult(c.vulnerable(), c.vulnerable() ? 0.9 : 0.1, 2);
    }

    private static CaseRunner alwaysVulnerable() {
        return (c, base, det) -> new CaseResult(true, 0.9, 2);
    }
}
