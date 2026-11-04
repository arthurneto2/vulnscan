package br.ufv.vulnscan.app.benchmark;

/** Classificação de um (caso × detector) contra o gabarito. */
public enum Outcome {
    TP, FP, TN, FN;

    /** Verdadeiro/falso positivo/negativo a partir do esperado (gabarito) e do previsto (detector). */
    public static Outcome of(boolean expected, boolean predicted) {
        if (expected) {
            return predicted ? TP : FN;
        }
        return predicted ? FP : TN;
    }
}
