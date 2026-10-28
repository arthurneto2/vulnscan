package br.ufv.vulnscan.domain.module;

import br.ufv.vulnscan.domain.model.Finding;
import br.ufv.vulnscan.domain.model.HttpRequest;
import br.ufv.vulnscan.domain.model.Target;
import br.ufv.vulnscan.domain.model.VulnType;
import br.ufv.vulnscan.port.HttpClient;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Domínio: módulo de fumaça (estratégia {@link VulnModule}). Faz um GET na URL base do alvo e
 * não reporta nada — só prova o caminho CLI → Scanner → HttpClient → relatório. Erro de rede
 * propaga, para o scan não sair "limpo" com o alvo fora do ar. Temporário: sai com o
 * primeiro módulo real (SqlInjectionModule, issue #10).
 */
public final class SmokeModule implements VulnModule {

    private final HttpClient http;

    /** @param http porta HTTP usada para a única requisição. */
    public SmokeModule(HttpClient http) {
        this.http = Objects.requireNonNull(http, "http");
    }

    /** Não há VulnType de fumaça; ninguém chama {@code VulnModule.type()} hoje. */
    @Override
    public VulnType type() {
        throw new UnsupportedOperationException("módulo de fumaça não tem VulnType");
    }

    @Override
    public List<Finding> scan(Target target) {
        http.send(new HttpRequest("GET", target.baseUrl(), Map.of(), null));
        return List.of();
    }
}
