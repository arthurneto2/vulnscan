package br.ufv.vulnscan.domain.module;

/**
 * Domínio: ponto de injeção de um módulo — requisição {@code GET} em {@code path} (relativo
 * à URL base do alvo; pode trazer query fixa, ex.: {@code /vulnerabilities/sqli/?Submit=Submit})
 * com o payload no parâmetro de query {@code parameter}.
 */
public record InjectionPoint(String path, String parameter) {}
