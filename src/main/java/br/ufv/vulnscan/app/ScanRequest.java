package br.ufv.vulnscan.app;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Aplicação: pedido de scan vindo da CLI. {@code modules} vazio = todos;
 * {@code out} nulo = {@code report.<format>}; {@code headers} (de {@code --header}) vão em
 * toda requisição ao alvo (nulo vira vazio); {@code detector} (de {@code --detector}) é
 * {@code heuristic} | {@code llm} | {@code both} (nulo/vazio vira {@code heuristic}).
 */
public record ScanRequest(URI target, List<String> modules, String format, Path out,
                          Map<String, String> headers, String detector) {

    private static final String DEFAULT_DETECTOR = "heuristic";

    public ScanRequest {
        headers = headers == null ? Map.of() : Map.copyOf(headers);
        detector = detector == null || detector.isBlank() ? DEFAULT_DETECTOR : detector;
    }

    /** Pedido com headers, detector {@code heuristic}. */
    public ScanRequest(URI target, List<String> modules, String format, Path out, Map<String, String> headers) {
        this(target, modules, format, out, headers, DEFAULT_DETECTOR);
    }

    /** Pedido sem headers extras, detector {@code heuristic}. */
    public ScanRequest(URI target, List<String> modules, String format, Path out) {
        this(target, modules, format, out, Map.of(), DEFAULT_DETECTOR);
    }
}
