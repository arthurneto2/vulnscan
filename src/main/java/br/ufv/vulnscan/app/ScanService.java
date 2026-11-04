package br.ufv.vulnscan.app;

import br.ufv.vulnscan.domain.Scanner;
import br.ufv.vulnscan.domain.detection.Detector;
import br.ufv.vulnscan.domain.model.ScanReport;
import br.ufv.vulnscan.domain.model.Target;
import br.ufv.vulnscan.domain.module.VulnModule;
import br.ufv.vulnscan.port.ReportWriter;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Aplicação: caso de uso de scan chamado pela CLI. Valida o pedido, escolhe os módulos e o(s)
 * detector(es) de {@code --detector}, cria cada módulo com cada detector escolhido, roda o
 * Scanner e grava o relatório. Erro de uso vira {@link InvalidScanRequestException} (antes de
 * qualquer requisição); erro de execução propaga e nada é gravado.
 */
public final class ScanService {

    private final Map<String, Function<Detector, VulnModule>> moduleFactories;
    private final Map<String, Detector> detectors;
    private final Map<String, ReportWriter> writers = new LinkedHashMap<>();

    /**
     * @param moduleFactories por nome de {@code --module}, na ordem de execução; cada fábrica
     *                        cria o módulo com o {@link Detector} escolhido (copiado)
     * @param detectors       por nome: {@code heuristic}, {@code llm} (copiado)
     * @param writers         um por formato, indexados por {@link ReportWriter#format()}
     */
    public ScanService(Map<String, Function<Detector, VulnModule>> moduleFactories,
                       Map<String, Detector> detectors, List<ReportWriter> writers) {
        this.moduleFactories = new LinkedHashMap<>(moduleFactories);
        this.detectors = new LinkedHashMap<>(detectors);
        writers.forEach(writer -> this.writers.put(writer.format(), writer));
    }

    public ScanReport scan(ScanRequest request) {
        URI url = validTarget(request.target());
        ReportWriter writer = writerFor(request.format());
        List<String> modules = selectModules(request.modules());
        List<Detector> chosen = selectDetectors(request.detector());

        List<VulnModule> built = new ArrayList<>();
        for (String name : modules) {
            for (Detector detector : chosen) {
                built.add(moduleFactories.get(name).apply(detector));
            }
        }

        ScanReport report = new Scanner(built).scan(new Target(url.getAuthority(), url, request.headers()));
        writer.write(report, request.out() != null ? request.out() : Path.of("report." + writer.format()));
        return report;
    }

    private static URI validTarget(URI target) {
        if (target == null || !target.isAbsolute()
                || !("http".equals(target.getScheme()) || "https".equals(target.getScheme()))
                || target.getHost() == null) {
            throw new InvalidScanRequestException(
                    "alvo inválido: " + target + " (use uma URL http(s) absoluta, ex.: http://localhost:3000)");
        }
        return target;
    }

    private ReportWriter writerFor(String format) {
        ReportWriter writer = writers.get(format);
        if (writer == null) {
            throw new InvalidScanRequestException(
                    "formato desconhecido: " + format + " (disponíveis: " + String.join(", ", writers.keySet()) + ")");
        }
        return writer;
    }

    /** Nomes dos módulos: todos se nenhum foi pedido; senão só os pedidos — na ordem do registro, sem repetir. */
    private List<String> selectModules(List<String> requested) {
        for (String name : requested) {
            if (!moduleFactories.containsKey(name)) {
                throw new InvalidScanRequestException("módulo desconhecido: " + name
                        + " (disponíveis: " + String.join(", ", moduleFactories.keySet()) + ")");
            }
        }
        return moduleFactories.keySet().stream()
                .filter(name -> requested.isEmpty() || requested.contains(name))
                .toList();
    }

    /** heuristic → [heuristic]; llm → [llm]; both → [heuristic, llm]. Desconhecido → erro de uso. */
    private List<Detector> selectDetectors(String choice) {
        List<String> names = switch (choice) {
            case "heuristic" -> List.of("heuristic");
            case "llm" -> List.of("llm");
            case "both" -> List.of("heuristic", "llm");
            default -> throw new InvalidScanRequestException(
                    "detector desconhecido: " + choice + " (use heuristic, llm ou both)");
        };
        return names.stream().map(detectors::get).toList();
    }
}
