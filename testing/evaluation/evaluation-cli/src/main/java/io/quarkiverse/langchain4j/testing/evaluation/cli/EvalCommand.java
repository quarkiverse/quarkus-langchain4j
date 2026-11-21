package io.quarkiverse.langchain4j.testing.evaluation.cli;

import java.nio.file.Path;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * The {@code langchain4j-eval} command: review pending evaluation results and turn them into baselines.
 */
@Command(name = "langchain4j-eval", description = "Review pending evaluation results", mixinStandardHelpOptions = true)
public final class EvalCommand {

    private EvalCommand() {
    }

    public static void main(String[] args) {
        System.exit(run(Path.of(""), new ConsoleTerminal(), args));
    }

    /**
     * Run the command.
     *
     * @param root the directory to look for pending results in
     * @param terminal the console to use
     * @param args the command line arguments
     * @return the exit code
     */
    static int run(Path root, Terminal terminal, String... args) {
        CommandLine commandLine = new CommandLine(new EvalCommand())
                .addSubcommand(new ReviewCommand(root, terminal))
                .addSubcommand(new AcceptCommand(root, terminal))
                .addSubcommand(new RejectCommand(root, terminal))
                .setOut(terminal.out())
                .setErr(terminal.err())
                .setExecutionExceptionHandler((e, cmd, parseResult) -> {
                    cmd.getErr().println(e.getMessage() != null ? e.getMessage() : e.getClass().getName());
                    return 1;
                });
        // picocli reports invalid input as 2; this command promises 1 for every failure
        int exit = commandLine.execute(args) == 0 ? 0 : 1;
        terminal.out().flush();
        terminal.err().flush();
        return exit;
    }
}
