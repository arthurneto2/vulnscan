package br.ufv.vulnscan.domain.detection;

import br.ufv.vulnscan.domain.model.Evidence;
import br.ufv.vulnscan.domain.model.Verdict;

/**
 * Julga se uma evidência indica vulnerabilidade. Estratégia interna do domínio (NÃO é uma
 * porta). Duas implementações:
 *   - HeuristicDetector: regras/regex, puro, sem I/O.
 *   - LlmDetector: usa a porta LlmClient para pedir um veredito ao modelo.
 *
 * Trocar uma pela outra é o que torna possível a avaliação empírica IA vs. heurística.
 */
public interface Detector {

    /** Avalia a evidência e devolve um veredito com confiança e justificativa. */
    Verdict judge(Evidence evidence);

    /** Identifica o detector no relatório e no experimento: "heuristic" ou "llm". */
    String name();
}
