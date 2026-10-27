package br.ufv.vulnscan.domain.model;

/** Uma falha encontrada: tipo e gravidade, com a evidência e o veredito que a sustentam. */
public record Finding(VulnType type, Severity severity, Target target,
                      Evidence evidence, Verdict verdict) {}
