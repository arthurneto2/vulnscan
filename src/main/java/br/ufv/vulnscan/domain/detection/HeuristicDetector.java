package br.ufv.vulnscan.domain.detection;

import br.ufv.vulnscan.domain.model.Evidence;
import br.ufv.vulnscan.domain.model.Verdict;

public final class HeuristicDetector implements Detector {

    @Override
    public Verdict judge(Evidence evidence) {
        return new Verdict(false, 0.0, "", "");
    }

    @Override
    public String name() {
        return "heuristic";
    }
}
