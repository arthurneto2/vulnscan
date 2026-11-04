package br.ufv.vulnscan.app;

import br.ufv.vulnscan.app.benchmark.BenchmarkRunner;
import br.ufv.vulnscan.app.benchmark.CaseRunner;
import br.ufv.vulnscan.app.benchmark.GroundTruth;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Aplicação: caso de uso do experimento chamado pela CLI. Valida o pedido, carrega o gabarito,
 * roda o {@link BenchmarkRunner} com os detectores dados (N vezes) e devolve o caminho do
 * {@code summary.csv}. Erro de uso vira {@link InvalidScanRequestException} (CLI: saída 1).
 */
public final class BenchmarkService {

    private final CaseRunner runner;
    private final List<String> detectors;

    /** @param detectors detectores a comparar, na ordem (ex.: {@code heuristic}, {@code llm}) */
    public BenchmarkService(CaseRunner runner, List<String> detectors) {
        this.runner = runner;
        this.detectors = List.copyOf(detectors);
    }

    public Path run(BenchmarkRequest request) {
        Path groundTruth = request.groundTruth();
        if (groundTruth == null || !Files.isRegularFile(groundTruth)) {
            throw new InvalidScanRequestException("gabarito não encontrado: " + groundTruth);
        }
        GroundTruth gt = GroundTruth.load(groundTruth);
        new BenchmarkRunner(gt, runner, detectors, request.runs()).run(request.outDir());
        return request.outDir().resolve("summary.csv");
    }
}
