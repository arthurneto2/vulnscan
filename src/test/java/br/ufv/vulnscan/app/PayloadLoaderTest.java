package br.ufv.vulnscan.app;

import br.ufv.vulnscan.domain.model.Payload;
import br.ufv.vulnscan.domain.model.VulnType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Testes do carregador de payloads: parse puro e leitura do recurso real do classpath. */
class PayloadLoaderTest {

    @Test
    void parseIgnoraComentariosELinhasEmBranco() {
        List<String> lines = List.of(
                "# comentário",
                "",
                "   ",
                "sqli-quote-1\t'\taspas simples",
                "sqli-error-1\tx' OR '1'='1\tcondição sempre verdadeira");

        List<Payload> payloads = PayloadLoader.parse(lines, VulnType.SQL_INJECTION);

        assertEquals(2, payloads.size());
        assertEquals("sqli-quote-1", payloads.get(0).id());
        assertEquals("'", payloads.get(0).value());
        assertEquals("aspas simples", payloads.get(0).description());
        assertEquals(VulnType.SQL_INJECTION, payloads.get(0).type());
        // espaços internos do value preservados
        assertEquals("x' OR '1'='1", payloads.get(1).value());
    }

    @Test
    void recursoRealSqliCarrega() {
        List<Payload> payloads = PayloadLoader.load("payloads/sqli.txt", VulnType.SQL_INJECTION);

        assertFalse(payloads.isEmpty(), "payloads/sqli.txt não pode ser vazio");
        assertTrue(payloads.stream().allMatch(p -> p.type() == VulnType.SQL_INJECTION));
        assertTrue(payloads.stream().anyMatch(p -> p.id().startsWith("sqli-time-")),
                "falta ao menos um payload temporal sqli-time-*");
        assertTrue(payloads.stream().anyMatch(p -> p.value().contains("'")),
                "falta ao menos um payload de aspas/erro");

        Set<String> ids = payloads.stream().map(Payload::id).collect(Collectors.toSet());
        assertEquals(payloads.size(), ids.size(), "ids de payload não podem repetir");
    }
}
