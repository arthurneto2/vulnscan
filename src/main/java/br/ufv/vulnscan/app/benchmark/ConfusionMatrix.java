package br.ufv.vulnscan.app.benchmark;

/**
 * Aplicação (experimento): matriz de confusão de um detector (por tipo ou total) e as métricas
 * derivadas. Denominador zero devolve 0.0 (sem sinal), não NaN.
 */
public final class ConfusionMatrix {

    private int tp;
    private int fp;
    private int tn;
    private int fn;

    public void add(Outcome outcome) {
        switch (outcome) {
            case TP -> tp++;
            case FP -> fp++;
            case TN -> tn++;
            case FN -> fn++;
        }
    }

    public int tp() {
        return tp;
    }

    public int fp() {
        return fp;
    }

    public int tn() {
        return tn;
    }

    public int fn() {
        return fn;
    }

    /** VP / (VP + FP). */
    public double precision() {
        return ratio(tp, tp + fp);
    }

    /** VP / (VP + FN). */
    public double recall() {
        return ratio(tp, tp + fn);
    }

    public double f1() {
        double p = precision();
        double r = recall();
        return ratio(2 * p * r, p + r);
    }

    /** FP / (FP + VN). */
    public double falsePositiveRate() {
        return ratio(fp, fp + tn);
    }

    private static double ratio(double numerator, double denominator) {
        return denominator == 0 ? 0.0 : numerator / denominator;
    }
}
