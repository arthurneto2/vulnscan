package br.ufv.vulnscan.app;

/** Aplicação: pedido de scan inválido (erro de uso; a CLI devolve código de saída 1). */
public final class InvalidScanRequestException extends RuntimeException {

    public InvalidScanRequestException(String message) {
        super(message);
    }
}
