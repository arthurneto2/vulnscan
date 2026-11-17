package br.ufv.vulnscan.app.benchmark;

import java.net.URI;
import java.util.List;

/**
 * Aplicação (experimento): costura que volta o laboratório ao estado de fábrica antes de uma
 * execução do benchmark e espera os alvos responderem. Não é porta (fica em {@code app/}, como o
 * {@link CaseRunner}); existe para o {@link BenchmarkRunner} ser testado com um dublê. A
 * implementação real é {@link DockerComposeLabReset}.
 */
public interface LabReset {

    /** Sem reset: o benchmark roda contra o lab como estiver. */
    LabReset NONE = baseUrls -> { };

    /**
     * Reseta o lab e espera cada alvo responder.
     *
     * @throws IllegalStateException se o reset falhar ou um alvo não responder no prazo
     */
    void reset(List<URI> baseUrls);
}
