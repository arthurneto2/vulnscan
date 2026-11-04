package br.ufv.vulnscan.app;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Aplicação: pedido de scan vindo da CLI. {@code modules} vazio = todos;
 * {@code out} nulo = {@code report.<format>}; {@code headers} (de {@code --header}) vão em
 * toda requisição ao alvo — nulo vira vazio.
 */
public record ScanRequest(URI target, List<String> modules, String format, Path out,
                          Map<String, String> headers) {

    public ScanRequest {
        headers = headers == null ? Map.of() : Map.copyOf(headers);
    }

    /** Pedido sem headers extras. */
    public ScanRequest(URI target, List<String> modules, String format, Path out) {
        this(target, modules, format, out, Map.of());
    }
}
