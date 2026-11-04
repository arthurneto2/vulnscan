package br.ufv.vulnscan.domain.module;

import br.ufv.vulnscan.domain.model.Payload;
import br.ufv.vulnscan.domain.model.VulnType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Testes do parser do arquivo de payloads: texto em memória, sem I/O. */
class PayloadFileTest {

    @Test
    void leLinhasValidas() {
        List<Payload> payloads = PayloadFile.parse(
                "sqli-a\t1'\taspas simples\nsqli-b\t1' OR '1'='1\tbooleano\n", VulnType.SQL_INJECTION);

        assertEquals(List.of(
                new Payload("sqli-a", VulnType.SQL_INJECTION, "1'", "aspas simples"),
                new Payload("sqli-b", VulnType.SQL_INJECTION, "1' OR '1'='1", "booleano")), payloads);
    }

    @Test
    void ignoraVaziasEComentarios() {
        List<Payload> payloads = PayloadFile.parse(
                "# cabeçalho\n\n   \nsqli-a\t1'\taspas\r\n# fim\n", VulnType.SQL_INJECTION);

        assertEquals(List.of(new Payload("sqli-a", VulnType.SQL_INJECTION, "1'", "aspas")), payloads);
    }

    @Test
    void linhaMalformadaCitaNumeroDaLinha() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> PayloadFile.parse("# ok\nsqli-a\t1'\taspas\nsem-tab\n", VulnType.SQL_INJECTION));

        assertTrue(e.getMessage().contains("linha 3"), e.getMessage());
    }

    @Test
    void campoVazioFalha() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> PayloadFile.parse("sqli-a\t\taspas\n", VulnType.SQL_INJECTION));

        assertTrue(e.getMessage().contains("linha 1"), e.getMessage());
    }

    @Test
    void idRepetidoFalha() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> PayloadFile.parse("sqli-a\t1'\tx\nsqli-a\t1\"\ty\n", VulnType.SQL_INJECTION));

        assertTrue(e.getMessage().contains("linha 2") && e.getMessage().contains("sqli-a"), e.getMessage());
    }

    @Test
    void arquivoSqliDoClasspathTemAspasBooleanoETemporal() throws IOException {
        List<Payload> payloads;
        try (InputStream in = PayloadFileTest.class.getResourceAsStream("/payloads/sqli.txt")) {
            assertNotNull(in, "recurso /payloads/sqli.txt ausente");
            payloads = PayloadFile.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8),
                    VulnType.SQL_INJECTION);
        }

        assertTrue(payloads.size() >= 5, "payloads: " + payloads);
        assertTrue(payloads.stream().anyMatch(p -> p.id().startsWith("sqli-quote-")), "aspas");
        assertTrue(payloads.stream().anyMatch(p -> p.id().startsWith("sqli-bool-")), "booleano");
        Payload last = payloads.get(payloads.size() - 1);
        assertTrue(last.id().startsWith("sqli-time-"), "o temporal vem por último: " + last);
    }
}
