package br.ufv.vulnscan.app.benchmark;

import br.ufv.vulnscan.app.benchmark.CaseRunner.CaseResult;
import br.ufv.vulnscan.app.benchmark.GroundTruth.Case;
import br.ufv.vulnscan.app.benchmark.GroundTruth.TargetCases;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Aplicação (experimento): roda cada caso do gabarito com cada detector, N vezes; classifica
 * contra a verdade, acumula a matriz de confusão por tipo e no total (por detector) e grava
 * {@code run-<n>.csv} e {@code summary.csv} (ver {@code docs/experiment.md}). O que executa um
 * caso é a costura {@link CaseRunner}; erro de um caso vira linha {@code outcome=ERRO} e o
 * benchmark segue.
 */
public final class BenchmarkRunner {

    private static final String TOTAL = "TOTAL";
    private static final String RUN_HEADER = "run,caso,tipo,detector,esperado,previsto,confianca,outcome,tempo_ms";
    private static final String SUMMARY_HEADER = "detector,tipo,tp,fp,tn,fn,precisao,recall,f1,fpr,tempo_medio_ms";

    private final GroundTruth groundTruth;
    private final CaseRunner runner;
    private final List<String> detectors;
    private final int runs;

    public BenchmarkRunner(GroundTruth groundTruth, CaseRunner runner, List<String> detectors, int runs) {
        this.groundTruth = groundTruth;
        this.runner = runner;
        this.detectors = List.copyOf(detectors);
        this.runs = runs;
    }

    /** Executa o experimento, grava os CSVs em {@code outDir} e devolve detector → (tipo|TOTAL) → matriz. */
    public Map<String, Map<String, ConfusionMatrix>> run(Path outDir) {
        try {
            Files.createDirectories(outDir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Map<String, Map<String, ConfusionMatrix>> matrices = new LinkedHashMap<>();
        Map<String, long[]> times = new LinkedHashMap<>(); // detector -> [somaMs, qtd]
        for (String detector : detectors) {
            matrices.put(detector, new LinkedHashMap<>());
            times.put(detector, new long[2]);
        }

        for (int n = 1; n <= runs; n++) {
            List<String> rows = new ArrayList<>();
            rows.add(RUN_HEADER);
            for (TargetCases target : groundTruth.targets()) {
                for (Case c : target.cases()) {
                    for (String detector : detectors) {
                        rows.add(runCase(n, target, c, detector, matrices, times));
                    }
                }
            }
            write(outDir.resolve("run-" + n + ".csv"), rows);
        }

        writeSummary(outDir.resolve("summary.csv"), matrices, times);
        return matrices;
    }

    private String runCase(int n, TargetCases target, Case c, String detector,
                           Map<String, Map<String, ConfusionMatrix>> matrices, Map<String, long[]> times) {
        try {
            CaseResult result = runner.run(c, target.baseUrl(), detector);
            Outcome outcome = Outcome.of(c.vulnerable(), result.predictedVulnerable());
            matrix(matrices, detector, c.type().name()).add(outcome);
            matrix(matrices, detector, TOTAL).add(outcome);
            long[] t = times.get(detector);
            t[0] += result.elapsedMillis();
            t[1]++;
            return String.join(",", String.valueOf(n), c.id(), c.type().name(), detector,
                    String.valueOf(c.vulnerable()), String.valueOf(result.predictedVulnerable()),
                    num(result.confidence()), outcome.name(), String.valueOf(result.elapsedMillis()));
        } catch (RuntimeException e) {
            return String.join(",", String.valueOf(n), c.id(), c.type().name(), detector,
                    String.valueOf(c.vulnerable()), "", "", "ERRO", "");
        }
    }

    private static ConfusionMatrix matrix(Map<String, Map<String, ConfusionMatrix>> matrices,
                                          String detector, String key) {
        return matrices.get(detector).computeIfAbsent(key, k -> new ConfusionMatrix());
    }

    private void writeSummary(Path file, Map<String, Map<String, ConfusionMatrix>> matrices,
                              Map<String, long[]> times) {
        List<String> rows = new ArrayList<>();
        rows.add(SUMMARY_HEADER);
        for (String detector : detectors) {
            long[] t = times.get(detector);
            double avg = t[1] == 0 ? 0.0 : (double) t[0] / t[1];
            for (Map.Entry<String, ConfusionMatrix> entry : matrices.get(detector).entrySet()) {
                ConfusionMatrix m = entry.getValue();
                rows.add(String.join(",", detector, entry.getKey(),
                        String.valueOf(m.tp()), String.valueOf(m.fp()), String.valueOf(m.tn()),
                        String.valueOf(m.fn()), num(m.precision()), num(m.recall()), num(m.f1()),
                        num(m.falsePositiveRate()), num(avg)));
            }
        }
        write(file, rows);
    }

    private static String num(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private static void write(Path file, List<String> rows) {
        try {
            Files.writeString(file, String.join("\n", rows) + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
