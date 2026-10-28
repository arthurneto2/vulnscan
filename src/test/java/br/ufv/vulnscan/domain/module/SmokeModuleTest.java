package br.ufv.vulnscan.domain.module;

import br.ufv.vulnscan.domain.model.HttpRequest;
import br.ufv.vulnscan.domain.model.HttpResponse;
import br.ufv.vulnscan.domain.model.Target;
import br.ufv.vulnscan.port.HttpClient;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void naoReportaNadaMesmoCom500() {
        RecordingHttpClient http = new RecordingHttpClient(response(500));

        assertTrue(new SmokeModule(http).scan(TARGET).isEmpty());
    }

    @Test
    void erroDoHttpClientPropaga() {
        UncheckedIOException error = new UncheckedIOException(new IOException("alvo fora do ar"));
        RecordingHttpClient http = new RecordingHttpClient(null, error);

        UncheckedIOException thrown = assertThrows(UncheckedIOException.class,
                () -> new SmokeModule(http).scan(TARGET));
        assertSame(error, thrown);
    }

    @Test
    void typeLancaUnsupportedOperation() {
        SmokeModule module = new SmokeModule(new RecordingHttpClient(response(200)));

        assertThrows(UnsupportedOperationException.class, module::type);
    }

    @Test
    void construtorRejeitaNull() {
        assertThrows(NullPointerException.class, () -> new SmokeModule(null));
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
