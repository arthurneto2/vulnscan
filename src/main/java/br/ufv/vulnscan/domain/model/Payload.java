package br.ufv.vulnscan.domain.model;

/** Um payload de ataque, carregado de arquivo ou gerado por um módulo. */
public record Payload(String id, VulnType type, String value, String description) {}
