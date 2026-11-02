package br.ufv.vulnscan.app;

import br.ufv.vulnscan.adapter.http.JdkHttpClient;
import br.ufv.vulnscan.adapter.report.MarkdownReportWriter;
import br.ufv.vulnscan.domain.detection.HeuristicDetector;
import br.ufv.vulnscan.domain.model.Payload;
import br.ufv.vulnscan.domain.model.VulnType;
import br.ufv.vulnscan.domain.module.SqlInjectionModule;
import br.ufv.vulnscan.domain.module.VulnModule;
import br.ufv.vulnscan.port.HttpClient;
import br.ufv.vulnscan.port.ReportWriter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aplicação: raiz de composição. Instancia os adaptadores e injeta nos módulos à mão,
 * sem framework de DI. Único lugar que conhece as implementações concretas.
 */
public final class Container {

    private final HttpClient httpClient;

    public Container(Config config) {
        this.httpClient = new JdkHttpClient(config.httpTimeout());
    }

    public ScanService scanService() {
        return new ScanService(modules(), writers());
    }

    /**
     * Módulos por nome de {@code --module}, na ordem de execução. Cada módulo recebe o
     * {@link #httpClient} e seu {@code Detector} por construtor; os payloads são lidos do
     * classpath aqui (o domínio não toca disco) e injetados. O detector heurístico é o
     * padrão da Fase 2; o {@code --detector llm|both} entra com a camada de IA.
     */
    private Map<String, VulnModule> modules() {
        List<Payload> sqliPayloads = PayloadLoader.load("payloads/sqli.txt", VulnType.SQL_INJECTION);
        Map<String, VulnModule> modules = new LinkedHashMap<>();
        modules.put("sqli", new SqlInjectionModule(httpClient, new HeuristicDetector(), sqliPayloads));
        return modules;
    }

    private List<ReportWriter> writers() {
        return List.of(new MarkdownReportWriter());
    }
}
