package br.ufv.vulnscan.app.benchmark;

import br.ufv.vulnscan.domain.model.HttpRequest;
import br.ufv.vulnscan.domain.model.HttpResponse;
import br.ufv.vulnscan.port.HttpClient;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Aplicação (experimento): decorador da porta {@link HttpClient} para o DVWA no nível
 * {@code impossible}, que exige um {@code user_token} anti-CSRF novo a cada submissão (sem
 * ele, responde 302 antes de executar a consulta). Antes de cada submissão (GET com query ou
 * POST com corpo), busca a página e acrescenta o token; a resposta devolvida é a da
 * submissão.
 */
final class DvwaCsrfHttpClient implements HttpClient {

    private final HttpClient inner;

    DvwaCsrfHttpClient(HttpClient inner) {
        this.inner = inner;
    }

    @Override
    public HttpResponse send(HttpRequest request) {
        String url = request.url().toString();
        int q = url.indexOf('?');
        boolean hasBody = request.body() != null;
        if (q < 0 && !hasBody) {
            return inner.send(request);
        }
        URI pageUrl = q < 0 ? request.url() : URI.create(url.substring(0, q));
        String token = DvwaSession.userToken(fetchPage(pageUrl, request.headers()).body());
        if (token.isEmpty()) {
            return inner.send(request);
        }
        String param = "user_token=" + token;
        return hasBody
                ? inner.send(new HttpRequest(request.method(), request.url(), request.headers(),
                        request.body() + "&" + param))
                : inner.send(new HttpRequest(request.method(), URI.create(url + "&" + param),
                        request.headers(), null));
    }

    private HttpResponse fetchPage(URI pageUrl, Map<String, String> headers) {
        Map<String, String> pageHeaders = new LinkedHashMap<>(headers);
        pageHeaders.remove("Content-Type");
        return inner.send(new HttpRequest("GET", pageUrl, Map.copyOf(pageHeaders), null));
    }
}
