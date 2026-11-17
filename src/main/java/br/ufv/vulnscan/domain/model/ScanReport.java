package br.ufv.vulnscan.domain.model;

import java.time.Instant;
import java.util.List;

/**
 * Resultado de um scan completo de um alvo. {@code errors} lista os módulos que falharam
 * (issue #58); vazio quando todos rodaram.
 */
public record ScanReport(Target target, Instant startedAt, Instant finishedAt,
                         List<Finding> findings, List<ModuleError> errors) {

    public ScanReport {
        errors = List.copyOf(errors);
    }

    /** Relatório sem erros de módulo. */
    public ScanReport(Target target, Instant startedAt, Instant finishedAt, List<Finding> findings) {
        this(target, startedAt, finishedAt, findings, List.of());
    }
}
