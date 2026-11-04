package br.ufv.vulnscan.cli;

import br.ufv.vulnscan.app.Config;
import br.ufv.vulnscan.app.Container;
import br.ufv.vulnscan.app.BenchmarkService;
import br.ufv.vulnscan.app.ScanService;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import java.util.concurrent.Callable;
import java.util.function.Supplier;

/**
 * CLI (adaptador de entrada): comando raiz {@code vulnscan}. Só traduz argumentos e
 * delega à aplicação. Códigos de saída: 0 ok, 1 erro de uso, 2 erro de execução.
 */
@Command(name = "vulnscan",
        description = "Scanner de vulnerabilidades web (heurística vs LLM) para o laboratório local.",
        subcommands = {ScanCommand.class, BenchmarkCommand.class},
        exitCodeOnInvalidInput = Main.EXIT_USAGE,
        exitCodeOnExecutionException = Main.EXIT_EXECUTION)
public final class Main implements Callable<Integer> {

    static final int EXIT_OK = 0;
    static final int EXIT_USAGE = 1;
    static final int EXIT_EXECUTION = 2;

    @Spec
    private CommandSpec spec;

    @Option(names = {"-h", "--help"}, usageHelp = true, description = "Mostra esta ajuda.")
    private boolean help;

    private final Supplier<ScanService> scanService;
    private final Supplier<BenchmarkService> benchmarkService;

    public Main() {
        Container container = new Container(Config.defaults());
        this.scanService = container::scanService;
        this.benchmarkService = container::benchmarkService;
    }

    Main(Supplier<ScanService> scanService) {
        this(scanService, () -> {
            throw new UnsupportedOperationException("benchmarkService não injetado");
        });
    }

    Main(Supplier<ScanService> scanService, Supplier<BenchmarkService> benchmarkService) {
        this.scanService = scanService;
        this.benchmarkService = benchmarkService;
    }

    public static void main(String[] args) {
        System.exit(new CommandLine(new Main()).execute(args));
    }

    ScanService scanService() {
        return scanService.get();
    }

    BenchmarkService benchmarkService() {
        return benchmarkService.get();
    }

    @Override
    public Integer call() {
        spec.commandLine().usage(spec.commandLine().getErr());
        return EXIT_USAGE;
    }
}
