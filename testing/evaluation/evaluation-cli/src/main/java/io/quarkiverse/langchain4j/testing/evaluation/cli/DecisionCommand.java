package io.quarkiverse.langchain4j.testing.evaluation.cli;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.quarkiverse.langchain4j.testing.evaluation.review.PendingResult;
import io.quarkiverse.langchain4j.testing.evaluation.review.ResultStatus;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * What {@code accept} and {@code reject} share: select results, confirm, apply.
 */
abstract class DecisionCommand implements Callable<Integer> {

    static class Selection {
        @Option(names = "--all", description = "All pending results")
        boolean all;
        @Parameters(arity = "1..*", paramLabel = "<id>", description = "The evaluation ids")
        List<String> ids;
    }

    @ArgGroup(exclusive = true, multiplicity = "1")
    Selection selection;

    @Option(names = "--yes", description = "Do not ask for confirmation")
    boolean yes;

    private final Path root;
    private final Terminal terminal;
    private final String verb;
    private final String past;
    private final String gerund;

    DecisionCommand(Path root, Terminal terminal, String verb, String past, String gerund) {
        this.root = root;
        this.terminal = terminal;
        this.verb = verb;
        this.past = past;
        this.gerund = gerund;
    }

    abstract void apply(PendingResult result);

    /**
     * The text to print under the count for one applied result.
     *
     * @return the text, or {@code null} for none
     */
    abstract String detail(PendingResult result);

    @Override
    public Integer call() {
        List<PendingResult> all = Targets.all(root, terminal);
        List<PendingResult> results = selection.all ? all : Targets.byIds(root, all, selection.ids, terminal);
        if (results == null) {
            return 1;
        }
        if (results.isEmpty()) {
            terminal.out().println("No pending evaluation results");
            return 0;
        }
        if (!yes) {
            if (!terminal.interactive()) {
                terminal.err().println("Not " + gerund
                        + ": no terminal to confirm with. Run this yourself, or pass --yes if a person asked for it.");
                return 1;
            }
            results.forEach(r -> terminal.out()
                    .println("  " + Targets.name(root, r, all) + "  " + r.status().name().toLowerCase(Locale.ROOT)));
            String answer = terminal.readLine(verb + " " + count(results) + " (" + byStatus(results) + ")? [y/N] ");
            if (answer == null || !answer.strip().toLowerCase().matches("y|yes")) {
                terminal.out().println("Nothing changed.");
                return 0;
            }
        }
        for (PendingResult result : results) {
            apply(result);
        }
        terminal.out().println(past + " " + count(results) + ".");
        results.forEach(r -> {
            String detail = detail(r);
            if (detail != null) {
                terminal.out().println("  " + detail);
            }
        });
        return 0;
    }

    private static String byStatus(List<PendingResult> results) {
        return Stream.of(ResultStatus.NEW, ResultStatus.CHANGED, ResultStatus.FAILED, ResultStatus.INCOMPATIBLE)
                .map(status -> results.stream().filter(r -> r.status() == status).count() + " "
                        + status.name().toLowerCase(Locale.ROOT))
                .filter(text -> !text.startsWith("0 ")).collect(Collectors.joining(", "));
    }

    private static String count(List<PendingResult> results) {
        return results.size() + (results.size() == 1 ? " evaluation" : " evaluations");
    }

    String relative(Path file) {
        return root.toAbsolutePath().normalize().relativize(file).toString();
    }
}
