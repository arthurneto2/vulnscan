package br.ufv.vulnscan.domain.model;

/** O que um Detector recebe para julgar: o payload aplicado e a troca HTTP resultante. */
public record Evidence(Payload payload, HttpRequest request, HttpResponse response) {}
