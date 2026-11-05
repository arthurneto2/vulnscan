package br.ufv.vulnscan.ci;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Garante que o workflow de release constrói e publica os artefatos distribuíveis. */
class ReleaseWorkflowTest {

    private static final Path WORKFLOW = Path.of(".github", "workflows", "release.yml");

    private static String content;

    @BeforeAll
    static void load() throws IOException {
        assertTrue(Files.exists(WORKFLOW), "workflow ausente: " + WORKFLOW.toAbsolutePath());
        content = Files.readString(WORKFLOW);
    }

    @Test
    void disparaEmTagsDeVersao() {
        assertTrue(content.contains("push:"), "sem gatilho push");
        assertTrue(content.contains("tags:"), "sem filtro de tags");
        assertTrue(content.contains("'v*'"), "sem filtro de tags v*");
    }

    @Test
    void usaJava21EEmpacotaOsArtefatos() {
        assertTrue(content.contains("java-version: '21'"), "sem Java 21");
        assertTrue(content.matches("(?s).*mvn[^\\n]*clean verify.*"), "sem 'mvn ... clean verify'");
        assertTrue(content.contains("target/vulnscan.jar"), "sem JAR executável");
        assertTrue(content.contains("vulnscan-${{ github.ref_name }}.tar.gz"),
                "sem pacote versionado");
        assertTrue(content.contains("softprops/action-gh-release@v2"),
                "sem publicação no GitHub Release");
    }
}
