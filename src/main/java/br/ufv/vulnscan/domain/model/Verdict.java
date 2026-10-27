package br.ufv.vulnscan.domain.model;

/**
 * Veredito de um Detector sobre uma Evidence.
 *
 * @param vulnerable  se indica vulnerabilidade
 * @param confidence  confiança em [0.0, 1.0]
 * @param rationale   justificativa em texto (vai para o relatório)
 * @param detector    quem emitiu: "heuristic" ou "llm"
 */
public record Verdict(boolean vulnerable, double confidence, String rationale, String detector) {}
