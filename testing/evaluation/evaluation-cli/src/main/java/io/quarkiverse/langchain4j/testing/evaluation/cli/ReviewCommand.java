package io.quarkiverse.langchain4j.testing.evaluation.cli;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;
import java.util.concurrent.Callable;

import io.quarkiverse.langchain4j.testing.evaluation.review.Comparison;
import io.quarkiverse.langchain4j.testing.evaluation.review.PendingResult;
import io.quarkiverse.langchain4j.testing.evaluation.review.ResultStatus;
import io.quarkiverse.langchain4j.testing.evaluation.review.Review;
import io.quarkiverse.langchain4j.testing.evaluation.review.SampleChange;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "review", description = "Review pending evaluation results", mixinStandardHelpOptions = true)
final class ReviewCommand implements Callable<Integer> {

    private static final String KEYS = "[a] accept  [r] reject  [d] show responses  [s] skip  [q] quit";

    @Option(names = "--json", description = "Print the pending results as JSON instead of prompting")
    boolean json;

    @Option(names = "--output", paramLabel = "<file>", description = "With --json, write the JSON to this file instead "
            + "of standard output, which a launcher such as JBang can also write to")
    Path output;

    @Parameters(arity = "0..*", paramLabel = "<id>", description = "Only review these evaluation ids")
    List<String> ids = List.of();

    private final Path root;
    private final Terminal terminal;

    ReviewCommand(Path root, Terminal terminal) {
        this.root = root;
        this.terminal = terminal;
    }

    @Override
    public Integer call() {
        List<PendingResult> all = Targets.all(root, terminal);
        List<PendingResult> results = ids.isEmpty() ? all : Targets.byIds(root, all, ids, terminal);
        if (results == null) {
            return 1;
        }
        if (output != null && !json) {
            terminal.err().println("--output needs --json");
            return 1;
        }
        if (json && output != null) {
            Path file = root.resolve(output);
            try {
                Path parent = file.toAbsolutePath().getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.writeString(file, Review.toJson(root, results) + System.lineSeparator());
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot write " + file, e);
            }
            terminal.out().println("Wrote " + results.size() + (results.size() == 1 ? " pending result" : " pending results")
                    + " to " + output);
        } else if (json) {
            terminal.out().println(Review.toJson(root, results));
        } else if (results.isEmpty()) {
            terminal.out().println("No pending evaluation results");
        } else if (!terminal.interactive()) {
            results.forEach(r -> terminal.out()
                    .println(Targets.name(root, r, all) + "  " + r.status().name().toLowerCase(Locale.ROOT)));
        } else {
            reviewInteractively(results, all);
        }
        return 0;
    }

    private void reviewInteractively(List<PendingResult> results, List<PendingResult> all) {
        for (int i = 0; i < results.size(); i++) {
            PendingResult result = results.get(i);
            String name = Targets.name(root, result, all);
            printTable(result, name, i + 1, results.size());
            while (true) {
                String answer = terminal.readLine("> ");
                String key = answer == null ? "q" : answer.strip().toLowerCase(Locale.ROOT);
                if (key.equals("a")) {
                    Review.accept(result);
                    terminal.out().println("Accepted " + name);
                    break;
                } else if (key.equals("r")) {
                    Review.reject(result);
                    terminal.out().println("Rejected " + name);
                    break;
                } else if (key.equals("s")) {
                    break;
                } else if (key.equals("q")) {
                    return;
                } else if (key.equals("d")) {
                    printResponses(result);
                } else {
                    terminal.out().println(KEYS);
                }
            }
        }
    }

    private void printTable(PendingResult result, String name, int position, int total) {
        var out = terminal.out();
        Comparison comparison = result.comparison();
        List<Review.Row> rows = Review.rows(result);
        out.println();
        out.println(name + "  " + result.status().name().toLowerCase(Locale.ROOT) + "  " + position + " of "
                + total);
        if (result.status() != ResultStatus.INCOMPATIBLE) {
            out.println(comparison.samples().size() + " compared, " + comparison.added().size() + " new, "
                    + comparison.removed().size() + " removed");
        }
        boolean advisory = result.summary().samples().stream().anyMatch(s -> s.advisory().total() > 0)
                || rows.stream().map(Review.Row::scores)
                        .anyMatch(s -> s.advisoryBaseline().isPresent() || s.advisoryReceived().isPresent());
        int width = "sample".length();
        for (Review.Row row : rows) {
            width = Math.max(width, row.scores().name().length());
        }
        String row = "  %-" + width + "s  %9s  %9s" + (advisory ? "  %9s  %9s" : "") + "  %s";
        if (advisory) {
            out.println(String.format("  %-" + width + "s  %20s  %20s", "", "blocking", "advisory").stripTrailing());
        }
        out.println(line(row, advisory, "sample", "baseline", "received", "baseline", "received", ""));
        for (Review.Row r : rows) {
            SampleChange scores = r.scores();
            String[] blocking = cells(r.kind(), scores.baseline(), scores.received());
            String[] advisoryCells = cells(r.kind(), scores.advisoryBaseline(), scores.advisoryReceived());
            String mark = r.kind() == Review.Kind.COMPARED && scores.regressed() ? "regressed" : "";
            out.println(line(row, advisory, scores.name(), blocking[0], blocking[1], advisoryCells[0], advisoryCells[1],
                    mark));
        }
        if (comparison.meanDifference().isPresent() && comparison.confidenceLow().isPresent()
                && comparison.confidenceHigh().isPresent()) {
            out.println(String.format(Locale.ROOT, "mean difference %.2f points (95%% CI %.2f to %.2f), tolerance %.2f",
                    comparison.meanDifference().getAsDouble(), comparison.confidenceLow().getAsDouble(),
                    comparison.confidenceHigh().getAsDouble(), comparison.tolerance()));
        }
        if (advisory && comparison.advisoryDifference().isPresent()) {
            out.println(String.format(Locale.ROOT, "advisory difference %.2f points, not checked against the tolerance",
                    comparison.advisoryDifference().getAsDouble()));
        }
        boolean compared = result.status() != ResultStatus.NEW && result.status() != ResultStatus.INCOMPATIBLE;
        if (compared && comparison.samples().stream().noneMatch(s -> s.baseline().isPresent()
                && s.received().isPresent())) {
            out.println("warning: no sample has blocking evaluations both here and in the baseline, "
                    + "so nothing was checked against the tolerance");
        }
        comparison.incompatibility().ifPresent(out::println);
        Review.errorSummary(Review.errors(result), Review.evaluations(result))
                .ifPresent(errors -> out.println("warning: " + errors));
        out.println(KEYS);
    }

    private static String line(String row, boolean advisory, String name, String baseline, String received,
            String advisoryBaseline, String advisoryReceived, String mark) {
        Object[] values = advisory ? new Object[] { name, baseline, received, advisoryBaseline, advisoryReceived, mark }
                : new Object[] { name, baseline, received, mark };
        return String.format(row, values).stripTrailing();
    }

    /**
     * The baseline and received cells of one kind of score.
     */
    private static String[] cells(Review.Kind kind, OptionalDouble baseline, OptionalDouble received) {
        return switch (kind) {
            case COMPARED, RECEIVED -> new String[] { percent(baseline), percent(received) };
            case NEW -> new String[] { received.isPresent() ? "new" : "", percent(received) };
            case REMOVED -> new String[] { percent(baseline), baseline.isPresent() ? "removed" : "" };
        };
    }

    private static String percent(OptionalDouble points) {
        return points.isPresent() ? percent(points.getAsDouble()) : "";
    }

    /**
     * Each trial's response once, with the verdict of every strategy on it beneath. A report without trials shows each
     * evaluation as its own response.
     */
    private void printResponses(PendingResult result) {
        var out = terminal.out();
        for (PendingResult.SampleDetail detail : result.details()) {
            out.println(detail.name());
            if (detail.expectedOutput() != null) {
                out.println("  expected: " + detail.expectedOutput());
            }
            int shown = -1;
            for (PendingResult.Response response : detail.responses()) {
                if (response.trial() == 0 || response.trial() != shown) {
                    String output = response.output() == null ? "(no output)" : response.output();
                    out.println("  " + (response.trial() == 0 ? "response" : "trial " + response.trial()) + ": " + output);
                    shown = response.trial();
                }
                out.println("    " + verdict(response));
            }
        }
    }

    private static String verdict(PendingResult.Response response) {
        StringBuilder line = new StringBuilder(response.passed() ? "passed" : "failed");
        if (response.strategy() != null) {
            line.append("  ").append(response.strategy());
        } else if (response.advisory()) {
            line.append("  (advisory)");
        }
        String reason = response.error() != null ? "error: " + response.error() : response.explanation();
        if (reason != null) {
            line.append(response.strategy() != null ? ": " : "  ").append(reason);
        }
        return line.toString();
    }

    private static String percent(double points) {
        return String.format(Locale.ROOT, "%.2f%%", points);
    }
}
