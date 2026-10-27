package br.ufv.vulnscan.port;

import br.ufv.vulnscan.domain.model.ScanReport;

import java.nio.file.Path;

/**
 * Porta de saída para gravar relatórios. Adaptadores: Markdown, Json, Html
 * (adapter/report/). Escolhido na CLI via --format.
 */
public interface ReportWriter {
    void write(ScanReport report, Path output);

    /** Formato produzido: "md" | "json" | "html". */
    String format();
}
