package br.ufv.vulnscan.domain.detection;

import br.ufv.vulnscan.domain.model.Evidence;
import br.ufv.vulnscan.domain.model.Verdict;

import java.util.Objects;

public final class HeuristicDetector implements Detector {

    private static final String NAME = "heuristic";

    @Override
    public Verdict judge(Evidence evidence) {
        Objects.requireNonNull(evidence, "evidence");
        return new Verdict(false, 0.6, "nenhuma assinatura de erro de SQL nem atraso temporal", NAME);
    }

    @Override
    public String name() {
        return NAME;
    }
}
