package br.ufv.vulnscan.app.benchmark;

import br.ufv.vulnscan.domain.detection.Detector;
import br.ufv.vulnscan.domain.model.Evidence;
import br.ufv.vulnscan.domain.model.Verdict;

import java.util.ArrayList;
import java.util.List;

/**
 * Aplicação (experimento): decorador de {@link Detector} que guarda os vereditos de um caso. O
 * módulo só devolve os achados (positivos); o {@link LabCaseRunner} precisa também dos negativos
 * para saber se algum foi de indisponibilidade do LLM (#74). Um por caso; não é thread-safe.
 */
final class RecordingDetector implements Detector {

    private final Detector delegate;
    private final List<Verdict> verdicts = new ArrayList<>();

    RecordingDetector(Detector delegate) {
        this.delegate = delegate;
    }

    @Override
    public Verdict judge(Evidence evidence) {
        Verdict verdict = delegate.judge(evidence);
        verdicts.add(verdict);
        return verdict;
    }

    @Override
    public String name() {
        return delegate.name();
    }

    /** Vereditos na ordem em que foram dados. */
    List<Verdict> verdicts() {
        return List.copyOf(verdicts);
    }
}
