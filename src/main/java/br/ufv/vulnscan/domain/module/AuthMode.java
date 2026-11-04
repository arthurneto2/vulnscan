package br.ufv.vulnscan.domain.module;

/**
 * Domínio: como o {@link ApiAuthModule} autentica uma requisição.
 * {@code NONE} sem {@code Authorization}; {@code USER_TOKEN} com o Bearer do usuário;
 * {@code USER_TOKEN_OTHER_RESOURCE} com o Bearer e {@code {otherId}} no path trocado por um
 * id diferente do próprio (IDOR).
 */
public enum AuthMode {
    NONE, USER_TOKEN, USER_TOKEN_OTHER_RESOURCE
}
