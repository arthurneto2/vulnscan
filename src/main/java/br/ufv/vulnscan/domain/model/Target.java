package br.ufv.vulnscan.domain.model;

import java.net.URI;

/** Alvo sob teste: um app do laboratório. */
public record Target(String name, URI baseUrl) {}
