package br.ufv.vulnscan.app.benchmark;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Testes puros do cálculo de métricas, com contagens fixas. */
class MetricsTest {

    @Test
    void outcomeClassificaOsQuatroCasos() {
        assertEquals(Outcome.TP, Outcome.of(true, true));
        assertEquals(Outcome.FN, Outcome.of(true, false));
        assertEquals(Outcome.FP, Outcome.of(false, true));
        assertEquals(Outcome.TN, Outcome.of(false, false));
    }

    @Test
    void metricasComContagensConhecidas() {
        ConfusionMatrix m = matrix(8, 2, 5, 3); // tp, fp, tn, fn

        assertEquals(8, m.tp());
        assertEquals(2, m.fp());
        assertEquals(5, m.tn());
        assertEquals(3, m.fn());
        assertEquals(0.8, m.precision(), 1e-9);              // 8/(8+2)
        assertEquals(8.0 / 11.0, m.recall(), 1e-9);           // 8/(8+3)
        double p = 0.8, r = 8.0 / 11.0;
        assertEquals(2 * p * r / (p + r), m.f1(), 1e-9);
        assertEquals(2.0 / 7.0, m.falsePositiveRate(), 1e-9); // 2/(2+5)
    }

    @Test
    void denominadorZeroDaZero() {
        ConfusionMatrix vazia = new ConfusionMatrix();

        assertEquals(0.0, vazia.precision());
        assertEquals(0.0, vazia.recall());
        assertEquals(0.0, vazia.f1());
        assertEquals(0.0, vazia.falsePositiveRate());
    }

    private static ConfusionMatrix matrix(int tp, int fp, int tn, int fn) {
        ConfusionMatrix m = new ConfusionMatrix();
        for (int i = 0; i < tp; i++) m.add(Outcome.TP);
        for (int i = 0; i < fp; i++) m.add(Outcome.FP);
        for (int i = 0; i < tn; i++) m.add(Outcome.TN);
        for (int i = 0; i < fn; i++) m.add(Outcome.FN);
        return m;
    }
}
