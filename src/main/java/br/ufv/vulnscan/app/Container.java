package br.ufv.vulnscan.app;

import br.ufv.vulnscan.adapter.http.JdkHttpClient;
import br.ufv.vulnscan.adapter.report.MarkdownReportWriter;
import br.ufv.vulnscan.domain.module.SmokeModule;
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
     * {@link #httpClient} (e o Detector) por construtor. O {@code smoke} é temporário: sai
     * quando entrar o primeiro módulo real (SqlInjectionModule, issue #10).
     */
    private Map<String, VulnModule> modules() {
        Map<String, VulnModule> modules = new LinkedHashMap<>();
        modules.put("smoke", new SmokeModule(httpClient));
        return modules;
    }

    private List<ReportWriter> writers() {
        return List.of(new MarkdownReportWriter());
    }
}
