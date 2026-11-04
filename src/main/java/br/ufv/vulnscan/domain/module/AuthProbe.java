package br.ufv.vulnscan.domain.module;

import br.ufv.vulnscan.domain.model.VulnType;

/**
 * Domínio: um teste de controle de acesso do {@link ApiAuthModule}: o {@code GET}
 * {@code pathTemplate} com a autenticação {@code mode}, esperando bloqueio. {@code type} é o
 * {@link VulnType} do achado; {@code name} identifica o caso.
 */
public record AuthProbe(VulnType type, String name, AuthMode mode, String pathTemplate) {}
