package br.ufv.vulnscan.domain.model;

/** Os tipos de falha dentro do escopo do trabalho. */
public enum VulnType {
    SQL_INJECTION,
    XSS_REFLECTED,
    XSS_STORED,
    BROKEN_AUTH,
    BROKEN_ACCESS_CONTROL
}
