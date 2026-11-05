package br.ufv.vulnscan.cli;

import br.ufv.vulnscan.app.BenchmarkRequest;
import br.ufv.vulnscan.app.InvalidScanRequestException;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * CLI: subcomando {@code benchmark}. Monta o BenchmarkRequest e chama o BenchmarkService;
 * pedido inválido sai com 1 e falha de execução com 2, sem stack trace.
 */
@Command(name = "benchmark",
        description = "Roda o experimento (heurística vs LLM) sobre o gabarito e grava os CSVs.",
        exitCodeOnInvalidInput = Main.EXIT_USAGE,
        exitCodeOnExecutionException = Main.EXIT_EXECUTION)
final class BenchmarkCommand implements Callable<Integer> {

    @ParentCommand
    private Main main;

    @Spec
    private CommandSpec spec;

    @Option(names = {"-h", "--help"}, usageHelp = true, description = "Mostra esta ajuda.")
    private boolean help;

    @Option(names = "--ground-truth", required = true, paramLabel = "<arquivo>",
            description = "Caminho do ground_truth.yaml.")
    private Path groundTruth;

    @Option(names = "--runs", defaultValue = "3", paramLabel = "<n>",
            description = "Número de execuções. Padrão: ${DEFAULT-VALUE}.")
    private int runs;

    @Option(names = "--llm", defaultValue = "ollama", paramLabel = "<backend>",
            description = "Backend do detector llm: ollama (local) ou api (compatível com OpenAI; chave em "
                    + "VULNSCAN_LLM_API_KEY). Padrão: ${DEFAULT-VALUE}.")
    private String llm;

    @Option(names = "--out", paramLabel = "<dir>",
            description = "Diretório dos CSVs. Padrão: experiments/results.")
    private Path out;

    @Override
    public Integer call() {
        try {
            Path summary = main.benchmarkService(llm).run(new BenchmarkRequest(groundTruth, runs, out));
            spec.commandLine().getOut().printf("Benchmark concluído: %d execução(ões). Resumo: %s%n",
                    runs, summary);
            return Main.EXIT_OK;
        } catch (InvalidScanRequestException e) {
            return error(e, Main.EXIT_USAGE);
        } catch (RuntimeException e) {
            return error(e, Main.EXIT_EXECUTION);
        }
    }

    private int error(RuntimeException e, int exitCode) {
        spec.commandLine().getErr().println("erro: " + e.getMessage());
        return exitCode;
    }
}
