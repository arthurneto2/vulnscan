package br.ufv.vulnscan.domain.module;

import br.ufv.vulnscan.domain.module.ApiSession.AuthSession;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Testes do record AuthSession. */
class AuthSessionTest {

    @Test
    void guardaTokenEId() {
        AuthSession session = new AuthSession("abc", "7");

        assertEquals("abc", session.token());
        assertEquals("7", session.ownResourceId());
    }

    @Test
    void camposNaoNulos() {
        assertThrows(NullPointerException.class, () -> new AuthSession(null, "7"));
        assertThrows(NullPointerException.class, () -> new AuthSession("abc", null));
    }
}
