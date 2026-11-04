package br.ufv.vulnscan.app.benchmark;

import br.ufv.vulnscan.app.benchmark.GroundTruth.Case;

import java.net.URI;

/** Costura de execução: roda um caso do gabarito com um detector e devolve o previsto e o tempo. */
public interface CaseRunner {

    CaseResult run(Case testCase, URI baseUrl, String detectorName);

    /** Previsto pelo detector (positivo/negativo), confiança do achado (0 se negativo) e tempo. */
    record CaseResult(boolean predictedVulnerable, double confidence, long elapsedMillis) {}
}
