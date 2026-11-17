package br.ufv.vulnscan.app;

import br.ufv.vulnscan.app.benchmark.BenchmarkRunner;
import br.ufv.vulnscan.app.benchmark.CaseRunner;
import br.ufv.vulnscan.app.benchmark.GroundTruth;
import br.ufv.vulnscan.app.benchmark.LabReset;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Aplicação: caso de uso do experimento chamado pela CLI. Valida o pedido, carrega o gabarito,
 * roda o {@link BenchmarkRunner} com os detectores dados (N vezes) e devolve o caminho do
 * {@code summary.csv}. Com {@code reset} no pedido, reseta o lab antes de cada execução (#60).
 * Erro de uso vira {@link InvalidScanRequestException} (CLI: saída 1).
 */
public final class BenchmarkService {

    private final CaseRunner runner;
    private final List<String> detectors;
    private final LabReset labReset;

    /** @param detectors detectores a comparar, na ordem (ex.: {@code heuristic}, {@code llm}) */
    public BenchmarkService(CaseRunner runner, List<String> detectors) {
        this(runner, detectors, LabReset.NONE);
    }

    /** @param labReset reset do lab entre execuções, usado só quando o pedido tem {@code reset} */
    public BenchmarkService(CaseRunner runner, List<String> detectors, LabReset labReset) {
        this.runner = runner;
        this.detectors = List.copyOf(detectors);
        this.labReset = labReset;
    }

    public Path run(BenchmarkRequest request) {
        Path groundTruth = request.groundTruth();
        if (groundTruth == null || !Files.isRegularFile(groundTruth)) {
            throw new InvalidScanRequestException("gabarito não encontrado: " + groundTruth);
        }
        GroundTruth gt = GroundTruth.load(groundTruth);
        LabReset reset = request.reset() ? labReset : LabReset.NONE;
        new BenchmarkRunner(gt, runner, detectors, request.runs(), reset).run(request.outDir());
        return request.outDir().resolve("summary.csv");
    }
}
