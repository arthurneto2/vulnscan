package br.ufv.vulnscan.domain.module;

import br.ufv.vulnscan.domain.model.Finding;
import br.ufv.vulnscan.domain.model.Target;
import br.ufv.vulnscan.domain.model.VulnType;

import java.util.List;

/**
 * Um tipo de ataque de ponta a ponta (SQLi, XSS, API). Estratégia interna do domínio.
 *
 * As implementações recebem seus colaboradores (HttpClient, Detector) por CONSTRUTOR —
 * injeção de dependência feita na raiz de composição (app/Container). O método scan só
 * recebe o alvo.
 */
public interface VulnModule {

    /** O tipo de falha que este módulo explora. */
    VulnType type();

    /** Ataca o alvo e devolve os achados (lista vazia se nada for encontrado). */
    List<Finding> scan(Target target);
}
