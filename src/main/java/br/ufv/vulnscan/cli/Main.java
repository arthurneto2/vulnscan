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
import java.util.function.Function;
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

    /** Serviços por backend do detector "llm" ({@code --llm}: ollama | api). */
    private final Function<String, ScanService> scanService;
    private final Function<String, BenchmarkService> benchmarkService;

    public Main() {
        Container container = new Container(Config.defaults());
        this.scanService = llm -> container.scanService(llm);
        this.benchmarkService = llm -> container.benchmarkService(llm);
    }

    Main(Supplier<ScanService> scanService) {
        this(scanService, () -> {
            throw new UnsupportedOperationException("benchmarkService não injetado");
        });
    }

    /** Para testes que não se importam com o backend do LLM. */
    Main(Supplier<ScanService> scanService, Supplier<BenchmarkService> benchmarkService) {
        this(llm -> scanService.get(), llm -> benchmarkService.get());
    }

    Main(Function<String, ScanService> scanService, Function<String, BenchmarkService> benchmarkService) {
        this.scanService = scanService;
        this.benchmarkService = benchmarkService;
    }

    public static void main(String[] args) {
        System.exit(new CommandLine(new Main()).execute(args));
    }

    ScanService scanService(String llm) {
        return scanService.apply(llm);
    }

    BenchmarkService benchmarkService(String llm) {
        return benchmarkService.apply(llm);
    }

    @Override
    public Integer call() {
        spec.commandLine().usage(spec.commandLine().getErr());
        return EXIT_USAGE;
    }
}
