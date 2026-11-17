package br.ufv.vulnscan.app;

import java.nio.file.Path;

/**
 * Aplicação: pedido de benchmark vindo da CLI. {@code runs} ausente (≤ 0) vira 3;
 * {@code outDir} nulo vira {@code experiments/results}. {@code reset}: resetar o lab antes de
 * cada execução ({@code --reset}, #60).
 */
public record BenchmarkRequest(Path groundTruth, int runs, Path outDir, boolean reset) {

    private static final int DEFAULT_RUNS = 3;
    private static final Path DEFAULT_OUT = Path.of("experiments", "results");

    public BenchmarkRequest {
        runs = runs <= 0 ? DEFAULT_RUNS : runs;
        outDir = outDir == null ? DEFAULT_OUT : outDir;
    }

    /** Pedido sem reset do lab entre as execuções. */
    public BenchmarkRequest(Path groundTruth, int runs, Path outDir) {
        this(groundTruth, runs, outDir, false);
    }
}
