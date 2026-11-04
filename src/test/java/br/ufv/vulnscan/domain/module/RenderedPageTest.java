package br.ufv.vulnscan.domain.module;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Testes do record RenderedPage: página aberta num navegador real. */
class RenderedPageTest {

    @Test
    void dialogosCopiadosEImutaveis() {
        List<String> dialogs = new ArrayList<>(List.of("vsx1"));
        RenderedPage page = new RenderedPage("<html></html>", dialogs, 10);

        dialogs.add("vsx2");

        assertEquals(List.of("vsx1"), page.dialogs());
        assertThrows(UnsupportedOperationException.class, () -> page.dialogs().add("x"));
    }

    @Test
    void dialogosNulosViramVazio() {
        assertEquals(List.of(), new RenderedPage("<html></html>", null, 10).dialogs());
    }
}
