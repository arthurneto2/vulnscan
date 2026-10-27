package br.ufv.vulnscan.app;

import br.ufv.vulnscan.domain.Scanner;
import br.ufv.vulnscan.domain.model.ScanReport;
import br.ufv.vulnscan.domain.model.Target;
import br.ufv.vulnscan.domain.module.VulnModule;
import br.ufv.vulnscan.port.ReportWriter;

import java.net.URI;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aplicação: caso de uso de scan chamado pela CLI. Valida o pedido, escolhe os módulos,
 * roda o Scanner e grava o relatório. Erro de uso vira {@link InvalidScanRequestException}
 * (antes de qualquer requisição); erro de execução propaga e nada é gravado.
 */
public final class ScanService {

    private final Map<String, VulnModule> modules;
    private final Map<String, ReportWriter> writers = new LinkedHashMap<>();

    /**
     * @param modules módulos por nome de {@code --module}, na ordem de execução (copiado)
     * @param writers um por formato, indexados por {@link ReportWriter#format()}
     */
    public ScanService(Map<String, VulnModule> modules, List<ReportWriter> writers) {
        this.modules = new LinkedHashMap<>(modules);
        writers.forEach(writer -> this.writers.put(writer.format(), writer));
    }

    public ScanReport scan(ScanRequest request) {
        URI url = validTarget(request.target());
        ReportWriter writer = writerFor(request.format());
        List<VulnModule> selected = selectModules(request.modules());

        ScanReport report = new Scanner(selected).scan(new Target(url.getAuthority(), url));
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

    /** Todos se nenhum foi pedido; senão só os pedidos — sempre na ordem do registro, sem repetir. */
    private List<VulnModule> selectModules(List<String> requested) {
        for (String name : requested) {
            if (!modules.containsKey(name)) {
                throw new InvalidScanRequestException(
                        "módulo desconhecido: " + name + " (disponíveis: " + String.join(", ", modules.keySet()) + ")");
            }
        }
        return modules.entrySet().stream()
                .filter(entry -> requested.isEmpty() || requested.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .toList();
    }
}
