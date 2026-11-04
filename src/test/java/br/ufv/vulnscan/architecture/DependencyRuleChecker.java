package br.ufv.vulnscan.architecture;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Teste de arquitetura: varre os fontes de {@code domain/} e {@code port/} e lista as
 * violações da regra de dependência de {@code docs/architecture.md}: referências a pacotes do
 * projeto que apontam para fora, e — no {@code domain/} — imports de bibliotecas externas ou
 * de rede/disco do JDK. Cada violação sai como {@code <arquivo>:<linha> -> <referência>}.
 *
 * <p>No domínio, um import só é permitido se for {@code java.*} (menos {@code java.net.http},
 * {@code java.nio.file} e {@code java.io.File*}), {@code br.ufv.vulnscan.*}, ou a allowlist
 * {@code com.fasterxml.jackson} (parse do veredito do LLM, decisão da issue #18).
 */
final class DependencyRuleChecker {

    /** Qualquer nome do projeto, inclusive curinga ({@code br.ufv.vulnscan.domain.*}). */
    private static final Pattern PROJECT_REFERENCE =
            Pattern.compile("br\\.ufv\\.vulnscan(?:\\.(?:\\w+|\\*))+");

    /** Prefixos de pacote do JDK proibidos no domínio (rede e disco). */
    private static final List<String> FORBIDDEN_JDK = List.of(
            "java.net.http", "java.nio.file", "java.io.File");

    /** Bibliotecas externas permitidas no domínio (exceção allowlisted). */
    private static final List<String> ALLOWED_LIBS = List.of("com.fasterxml.jackson");

    /** import [static] <fqn>; */
    private static final Pattern IMPORT = Pattern.compile("^\\s*import\\s+(?:static\\s+)?([\\w.]+)\\s*;");

    private record Rule(String layer, Predicate<String> allowed) {
    }

    private static final List<Rule> RULES = List.of(
            new Rule("domain", ref -> !isWithin(ref, "br.ufv.vulnscan.adapter")
                    && !isWithin(ref, "br.ufv.vulnscan.app")
                    && !isWithin(ref, "br.ufv.vulnscan.cli")),
            new Rule("port", ref -> isWithin(ref, "br.ufv.vulnscan.domain.model")
                    || isWithin(ref, "br.ufv.vulnscan.port")));

    private DependencyRuleChecker() {
    }

    static List<String> violations(Path sourceRoot) throws IOException {
        List<String> violations = new ArrayList<>();
        for (Rule rule : RULES) {
            Path layerDir = sourceRoot.resolve("br/ufv/vulnscan").resolve(rule.layer());
            if (!Files.isDirectory(layerDir)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(layerDir)) {
                for (Path file : files.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
                    violations.addAll(check(sourceRoot.relativize(file), Files.readAllLines(file), rule));
                }
            }
        }
        return violations;
    }

    private static List<String> check(Path file, List<String> lines, Rule rule) {
        List<String> violations = new ArrayList<>();
        boolean domain = rule.layer().equals("domain");
        for (int i = 0; i < lines.size(); i++) {
            Matcher m = PROJECT_REFERENCE.matcher(lines.get(i));
            while (m.find()) {
                if (!rule.allowed().test(m.group())) {
                    violations.add(file + ":" + (i + 1) + " -> " + m.group());
                }
            }
            if (domain) {
                String forbidden = forbiddenDomainImport(lines.get(i));
                if (forbidden != null) {
                    violations.add(file + ":" + (i + 1) + " -> " + forbidden);
                }
            }
        }
        return violations;
    }

    /**
     * Pacote proibido num import do domínio (lib externa fora da allowlist, ou rede/disco do
     * JDK), ou {@code null} se a linha não for um import proibido.
     */
    static String forbiddenDomainImport(String line) {
        Matcher m = IMPORT.matcher(line);
        if (!m.find()) {
            return null;
        }
        String fqn = m.group(1);
        for (String prefix : FORBIDDEN_JDK) {
            if (fqn.startsWith(prefix)) {
                return fqn;
            }
        }
        if (fqn.startsWith("java.") || fqn.startsWith("br.ufv.vulnscan.")) {
            return null;
        }
        for (String lib : ALLOWED_LIBS) {
            if (fqn.equals(lib) || fqn.startsWith(lib + ".")) {
                return null;
            }
        }
        return fqn;
    }

    private static boolean isWithin(String reference, String pkg) {
        return reference.equals(pkg) || reference.startsWith(pkg + ".");
    }
}
