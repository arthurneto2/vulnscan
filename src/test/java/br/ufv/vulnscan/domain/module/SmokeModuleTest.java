package br.ufv.vulnscan.domain.module;

import br.ufv.vulnscan.domain.model.HttpRequest;
import br.ufv.vulnscan.domain.model.HttpResponse;
import br.ufv.vulnscan.domain.model.Target;
import br.ufv.vulnscan.port.HttpClient;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Testes do módulo de fumaça com HttpClient fake, sem rede. */
class SmokeModuleTest {

    private static final Target TARGET = new Target("juice-shop", URI.create("http://localhost:3000"));

    @Test
    void enviaUmGetParaABaseUrl() {
        RecordingHttpClient http = new RecordingHttpClient(response(200));

        new SmokeModule(http).scan(TARGET);

        assertEquals(1, http.requests.size(), "quantidade de requisições");
        HttpRequest request = http.requests.get(0);
        assertEquals("GET", request.method());
        assertEquals(TARGET.baseUrl(), request.url());
        assertEquals(Map.of(), request.headers());
        assertNull(request.body());
    }

    private static HttpResponse response(int status) {
        return new HttpResponse(status, Map.of(), "", 1);
    }

    /** Guarda as requisições e devolve uma resposta fixa (ou lança, se configurado). */
    private static final class RecordingHttpClient implements HttpClient {
        final List<HttpRequest> requests = new ArrayList<>();
        private final HttpResponse response;
        private final RuntimeException error;

        RecordingHttpClient(HttpResponse response) {
            this(response, null);
        }

        RecordingHttpClient(HttpResponse response, RuntimeException error) {
            this.response = response;
            this.error = error;
        }

        @Override
        public HttpResponse send(HttpRequest request) {
            requests.add(request);
            if (error != null) {
                throw error;
            }
            return response;
        }
    }
}
