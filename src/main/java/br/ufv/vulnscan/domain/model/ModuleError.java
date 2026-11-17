package br.ufv.vulnscan.domain.model;

import java.util.Objects;

/**
 * Domínio: falha de um módulo durante o scan (issue #58). O {@code Scanner} registra a falha e
 * segue com os outros módulos; o relatório mostra o erro em vez de parecer "limpo".
 *
 * @param module  nome simples da classe do módulo (ex.: {@code ApiAuthModule})
 * @param message mensagem da exceção (ou o nome da exceção, se ela não tiver mensagem)
 */
public record ModuleError(String module, String message) {

    public ModuleError {
        Objects.requireNonNull(module, "module");
        Objects.requireNonNull(message, "message");
    }
}
