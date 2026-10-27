package br.ufv.vulnscan.domain.model;

import java.time.Instant;
import java.util.List;

/** Resultado de um scan completo de um alvo. */
public record ScanReport(Target target, Instant startedAt, Instant finishedAt,
                         List<Finding> findings) {}
