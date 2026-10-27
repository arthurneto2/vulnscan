package br.ufv.vulnscan.domain.model;

import java.net.URI;
import java.util.Map;

/** Requisição HTTP enviada pela porta HttpClient. */
public record HttpRequest(String method, URI url, Map<String, String> headers, String body) {}
