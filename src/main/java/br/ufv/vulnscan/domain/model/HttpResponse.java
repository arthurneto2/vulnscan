package br.ufv.vulnscan.domain.model;

import java.util.Map;

/** Resposta HTTP devolvida pela porta HttpClient. elapsedMillis mede o tempo da troca. */
public record HttpResponse(int status, Map<String, String> headers, String body, long elapsedMillis) {}
