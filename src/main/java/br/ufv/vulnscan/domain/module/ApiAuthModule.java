package br.ufv.vulnscan.domain.module;

import br.ufv.vulnscan.domain.detection.Detector;
import br.ufv.vulnscan.domain.model.Evidence;
import br.ufv.vulnscan.domain.model.Finding;
import br.ufv.vulnscan.domain.model.HttpRequest;
import br.ufv.vulnscan.domain.model.HttpResponse;
import br.ufv.vulnscan.domain.model.Payload;
import br.ufv.vulnscan.domain.model.Severity;
import br.ufv.vulnscan.domain.model.Target;
import br.ufv.vulnscan.domain.model.Verdict;
import br.ufv.vulnscan.domain.model.VulnType;
import br.ufv.vulnscan.domain.module.ApiSession.AuthSession;
import br.ufv.vulnscan.port.HttpClient;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Domínio: módulo de autenticação/autorização em API REST (estratégia {@link VulnModule}).
 * Cada {@link AuthProbe} é uma requisição montada para ser indevida — sem token
 * ({@link AuthMode#NONE}), com token de usuário ({@link AuthMode#USER_TOKEN}, papel indevido)
 * ou com token acessando o recurso de outro ({@link AuthMode#USER_TOKEN_OTHER_RESOURCE},
 * IDOR). Monta a {@link Evidence} e pede o veredito ao {@link Detector} (regras em
 * {@code HeuristicDetector}); positivo vira {@link Finding} {@link Severity#HIGH} do tipo do
 * probe. Cada probe é um caso independente — não encerra os outros.
 *
 * <p>O login é feito uma vez e só se algum probe precisa de token. Erros do {@link HttpClient}
 * e da {@link ApiSession} propagam.
 *
 * <p>Cobre dois {@link VulnType}; {@link #type()} devolve {@code BROKEN_AUTH} só para a
 * interface — cada {@code Finding} usa o tipo do seu probe.
 */
public final class ApiAuthModule implements VulnModule {

    private final HttpClient http;
    private final Detector detector;
    private final ApiSession session;
    private final List<AuthProbe> probes;

    public ApiAuthModule(HttpClient http, Detector detector, ApiSession session, List<AuthProbe> probes) {
        this.http = Objects.requireNonNull(http, "http");
        this.detector = Objects.requireNonNull(detector, "detector");
        this.session = Objects.requireNonNull(session, "session");
        this.probes = List.copyOf(Objects.requireNonNull(probes, "probes"));
        if (this.probes.isEmpty()) {
            throw new IllegalArgumentException("probes não podem ser vazios");
        }
    }

    @Override
    public VulnType type() {
        return VulnType.BROKEN_AUTH;
    }

    @Override
    public List<Finding> scan(Target target) {
        AuthSession auth = needsToken() ? session.authenticate(target) : null;
        List<Finding> findings = new ArrayList<>();
        for (AuthProbe probe : probes) {
            Evidence evidence = probe(target, probe, auth);
            Verdict verdict = detector.judge(evidence);
            if (verdict.vulnerable()) {
                findings.add(new Finding(probe.type(), Severity.HIGH, target, evidence, verdict));
            }
        }
        return List.copyOf(findings);
    }

    private boolean needsToken() {
        return probes.stream().anyMatch(probe -> probe.mode() != AuthMode.NONE);
    }

    private Evidence probe(Target target, AuthProbe probe, AuthSession auth) {
        String path = probe.pathTemplate();
        Map<String, String> headers = new LinkedHashMap<>(target.headers());
        if (probe.mode() != AuthMode.NONE) {
            headers.put("Authorization", "Bearer " + auth.token());
        }
        if (probe.mode() == AuthMode.USER_TOKEN_OTHER_RESOURCE) {
            path = path.replace("{otherId}", otherResource(auth.ownResourceId()));
        }
        URI url = target.baseUrl().resolve(path);
        HttpRequest request = new HttpRequest("GET", url, Map.copyOf(headers), null);
        HttpResponse response = http.send(request);
        Payload payload = new Payload(probe.name(), probe.type(), path, "probe de " + probe.mode());
        return new Evidence(payload, request, response);
    }

    /** Vizinho do id próprio: se inteiro e > 1, {@code n-1}; senão {@code n+1}. */
    private static String otherResource(String ownId) {
        try {
            long n = Long.parseLong(ownId);
            return String.valueOf(n > 1 ? n - 1 : n + 1);
        } catch (NumberFormatException e) {
            return ownId + "x";
        }
    }
}
