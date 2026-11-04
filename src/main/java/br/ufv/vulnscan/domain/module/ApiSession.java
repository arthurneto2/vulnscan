package br.ufv.vulnscan.domain.module;

import br.ufv.vulnscan.domain.model.Target;

import java.util.Objects;

/**
 * Domínio: interface pequena do {@link ApiAuthModule} para autenticar num alvo — isola o login
 * específico do alvo (parse de JSON) do domínio, como o {@code XssBrowser} isola o Selenium.
 * Não é porta; o adaptador é {@code adapter/http/JuiceShopSession}.
 */
public interface ApiSession {

    /** Registra/loga e devolve o token e o id do recurso próprio (ex.: cesta), para o IDOR. */
    AuthSession authenticate(Target target);

    /** Token de acesso e id do recurso próprio; ambos não nulos. */
    record AuthSession(String token, String ownResourceId) {
        public AuthSession {
            Objects.requireNonNull(token, "token");
            Objects.requireNonNull(ownResourceId, "ownResourceId");
        }
    }
}
