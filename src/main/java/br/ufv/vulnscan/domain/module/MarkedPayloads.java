package br.ufv.vulnscan.domain.module;

import br.ufv.vulnscan.domain.model.Payload;
import br.ufv.vulnscan.domain.model.VulnType;

import java.util.List;
import java.util.Objects;

/**
 * Domínio: payloads de XSS com o marcador {@value #MARKER} — validação comum aos módulos de
 * XSS e troca do marcador por um valor único a cada envio. Uso interno do pacote.
 */
final class MarkedPayloads {

    static final String MARKER = "{marker}";

    private MarkedPayloads() {
    }

    /**
     * Copia e valida: não vazia, todos do {@code type} e com {@value #MARKER}.
     *
     * @throws IllegalArgumentException citando o id do payload inválido
     */
    static List<Payload> validated(List<Payload> payloads, VulnType type) {
        List<Payload> copy = List.copyOf(Objects.requireNonNull(payloads, "payloads"));
        if (copy.isEmpty()) {
            throw new IllegalArgumentException("payloads não podem ser vazios");
        }
        for (Payload payload : copy) {
            if (payload.type() != type) {
                throw new IllegalArgumentException("payload " + payload.id() + " é " + payload.type()
                        + ", esperado " + type);
            }
            if (!payload.value().contains(MARKER)) {
                throw new IllegalArgumentException("payload " + payload.id() + " não contém " + MARKER);
            }
        }
        return copy;
    }

    /** Mesmo payload, com {@value #MARKER} trocado por {@code marker}. */
    static Payload withMarker(Payload template, String marker) {
        return new Payload(template.id(), template.type(),
                template.value().replace(MARKER, marker), template.description());
    }
}
