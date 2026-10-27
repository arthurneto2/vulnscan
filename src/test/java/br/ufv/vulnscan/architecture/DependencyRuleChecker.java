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
 * referências a pacotes do projeto que violam a regra de dependência de
 * {@code docs/architecture.md}. Cada violação sai como {@code <arquivo>:<linha> -> <pacote>}.
 */
final class DependencyRuleChecker {

    /** Qualquer nome do projeto, inclusive curinga ({@code br.ufv.vulnscan.domain.*}). */
    private static final Pattern PROJECT_REFERENCE =
            Pattern.compile("br\\.ufv\\.vulnscan(?:\\.(?:\\w+|\\*))+");

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
        for (int i = 0; i < lines.size(); i++) {
            Matcher m = PROJECT_REFERENCE.matcher(lines.get(i));
            while (m.find()) {
                if (!rule.allowed().test(m.group())) {
                    violations.add(file + ":" + (i + 1) + " -> " + m.group());
                }
            }
        }
        return violations;
    }

    private static boolean isWithin(String reference, String pkg) {
        return reference.equals(pkg) || reference.startsWith(pkg + ".");
    }
}
