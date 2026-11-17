package br.ufv.vulnscan.domain;

import br.ufv.vulnscan.domain.model.Finding;
import br.ufv.vulnscan.domain.model.ModuleError;
import br.ufv.vulnscan.domain.model.ScanReport;
import br.ufv.vulnscan.domain.model.Target;
import br.ufv.vulnscan.domain.module.VulnModule;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Domínio: orquestra os VulnModule sobre um alvo e monta o ScanReport.
 * Roda os módulos na ordem recebida e junta os findings nessa ordem. Uma {@link RuntimeException}
 * de um módulo não interrompe o scan (issue #58): vira {@link ModuleError} no relatório, os
 * achados desse módulo são descartados e os outros módulos seguem. O erro fica visível no
 * relatório e no código de saída da CLI, então o relatório parcial não parece "limpo".
 * {@link Error} da JVM propaga.
 */
public final class Scanner {

    private final List<VulnModule> modules;

    /** @param modules módulos a rodar, já escolhidos pela aplicação (lista copiada). */
    public Scanner(List<VulnModule> modules) {
        this.modules = List.copyOf(Objects.requireNonNull(modules, "modules"));
    }

    public ScanReport scan(Target target) {
        Objects.requireNonNull(target, "target");
        Instant startedAt = Instant.now();
        List<Finding> findings = new ArrayList<>();
        List<ModuleError> errors = new ArrayList<>();
        for (VulnModule module : modules) {
            try {
                findings.addAll(module.scan(target));
            } catch (RuntimeException e) {
                errors.add(new ModuleError(module.getClass().getSimpleName(),
                        Objects.requireNonNullElse(e.getMessage(), e.getClass().getSimpleName())));
            }
        }
        return new ScanReport(target, startedAt, Instant.now(), List.copyOf(findings), errors);
    }
}
