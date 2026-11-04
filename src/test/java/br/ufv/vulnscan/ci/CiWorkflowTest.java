package br.ufv.vulnscan.ci;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Garante que o workflow de CI existe e roda mvn clean verify com Java 21, sem rede. */
class CiWorkflowTest {

    private static final Path WORKFLOW = Path.of(".github", "workflows", "ci.yml");

    private static String content;

    @BeforeAll
    static void load() throws IOException {
        assertTrue(Files.exists(WORKFLOW), "workflow ausente: " + WORKFLOW.toAbsolutePath());
        content = Files.readString(WORKFLOW);
    }

    @Test
    void disparaEmPushEPullRequest() {
        assertTrue(content.contains("push:"), "sem gatilho push");
        assertTrue(content.contains("pull_request:"), "sem gatilho pull_request");
    }

    @Test
    void usaJava21() {
        assertTrue(content.contains("21"), "sem Java 21: " + content);
    }

    @Test
    void rodaCleanVerify() {
        assertTrue(content.matches("(?s).*mvn[^\\n]*clean verify.*"), "sem 'mvn ... clean verify'");
    }
}
