package br.ufv.vulnscan.app;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;

/**
 * Aplicação: pedido de scan vindo da CLI. {@code modules} vazio = todos;
 * {@code out} nulo = {@code report.<format>}.
 */
public record ScanRequest(URI target, List<String> modules, String format, Path out) {}
