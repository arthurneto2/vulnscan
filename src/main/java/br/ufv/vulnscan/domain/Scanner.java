package br.ufv.vulnscan.domain;

import br.ufv.vulnscan.domain.model.Finding;
import br.ufv.vulnscan.domain.model.ScanReport;
import br.ufv.vulnscan.domain.model.Target;
import br.ufv.vulnscan.domain.module.VulnModule;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Domínio: orquestra os VulnModule sobre um alvo e monta o ScanReport.
 * Roda os módulos na ordem recebida e junta os findings nessa ordem. Uma exceção de
 * módulo interrompe o scan e propaga: sem campo de erro no ScanReport, engolir a falha
 * produziria um relatório "limpo" falso.
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
        for (VulnModule module : modules) {
            findings.addAll(module.scan(target));
        }
        return new ScanReport(target, startedAt, Instant.now(), List.copyOf(findings));
    }
}
