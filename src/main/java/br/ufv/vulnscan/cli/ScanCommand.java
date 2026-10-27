package br.ufv.vulnscan.cli;

import br.ufv.vulnscan.app.InvalidScanRequestException;
import br.ufv.vulnscan.app.ScanRequest;
import br.ufv.vulnscan.domain.model.ScanReport;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

/**
 * CLI: subcomando {@code scan}. Monta o ScanRequest, avisa se o alvo não é local e chama
 * o ScanService; pedido inválido sai com 1 e falha de execução com 2, sem stack trace.
 */
@Command(name = "scan",
        description = "Roda um ou mais módulos contra um alvo e grava o relatório.",
        exitCodeOnInvalidInput = Main.EXIT_USAGE,
        exitCodeOnExecutionException = Main.EXIT_EXECUTION)
final class ScanCommand implements Callable<Integer> {

    private static final Pattern LOOPBACK_V4 = Pattern.compile("127(\\.\\d{1,3}){3}");

    @ParentCommand
    private Main main;

    @Spec
    private CommandSpec spec;

    @Option(names = {"-h", "--help"}, usageHelp = true, description = "Mostra esta ajuda.")
    private boolean help;

    @Option(names = "--target", required = true, paramLabel = "<url>",
            description = "URL base do alvo, ex.: http://localhost:3000")
    private URI target;

    @Option(names = "--module", paramLabel = "<nome>",
            description = "Módulo a rodar (sqli, xss, api); repetível. Padrão: todos.")
    private List<String> modules = new ArrayList<>();

    @Option(names = "--format", defaultValue = "md", paramLabel = "<fmt>",
            description = "Formato do relatório. Padrão: ${DEFAULT-VALUE}.")
    private String format;

    @Option(names = "--out", paramLabel = "<arquivo>",
            description = "Arquivo de saída. Padrão: report.<fmt>.")
    private Path out;

    @Override
    public Integer call() {
        Path output = out != null ? out : Path.of("report." + format);
        String host = target.getHost();
        if (host != null && !isLocal(host)) {
            spec.commandLine().getErr().printf(
                    "aviso: o alvo %s não é localhost; o vulnscan é para o laboratório local.%n", host);
        }
        try {
            ScanReport report = main.scanService().scan(new ScanRequest(target, modules, format, output));
            spec.commandLine().getOut().printf("Scan concluído: %d achado(s). Relatório: %s%n",
                    report.findings().size(), output);
            return Main.EXIT_OK;
        } catch (InvalidScanRequestException e) {
            return error(e, Main.EXIT_USAGE);
        } catch (RuntimeException e) {
            return error(e, Main.EXIT_EXECUTION);
        }
    }

    /** localhost, 127.0.0.0/8 ou [::1] — por texto, sem resolução DNS. */
    private static boolean isLocal(String host) {
        return host.equalsIgnoreCase("localhost")
                || LOOPBACK_V4.matcher(host).matches()
                || host.equals("[::1]");
    }

    private int error(RuntimeException e, int exitCode) {
        spec.commandLine().getErr().println("erro: " + e.getMessage());
        return exitCode;
    }
}
