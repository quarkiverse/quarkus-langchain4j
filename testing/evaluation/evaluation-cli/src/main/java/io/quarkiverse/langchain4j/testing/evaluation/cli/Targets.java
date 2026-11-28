package io.quarkiverse.langchain4j.testing.evaluation.cli;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import io.quarkiverse.langchain4j.testing.evaluation.review.PendingResult;
import io.quarkiverse.langchain4j.testing.evaluation.review.PendingResults;
import io.quarkiverse.langchain4j.testing.evaluation.review.ResultStatus;

/**
 * Finds the results the commands act on. Unchanged results need no action, so they are invisible.
 * <p>
 * Two modules can have an evaluation with the same id. Such an id is then written {@code <module>:<id>}, with the
 * module relative to the root, {@code .} for the root itself.
 * </p>
 */
final class Targets {

    private Targets() {
    }

    /**
     * Find the results, reporting what could not be read on {@code terminal}.
     */
    static List<PendingResult> all(Path root, Terminal terminal) {
        return PendingResults.scan(root, skipped -> terminal.err().println("Skipped " + skipped)).stream()
                .filter(r -> r.status() != ResultStatus.UNCHANGED).toList();
    }

    /**
     * Resolve ids to some of {@code all} results, or report the first unknown or ambiguous id on {@code terminal} and
     * return {@code null}.
     */
    static List<PendingResult> byIds(Path root, List<PendingResult> all, List<String> ids, Terminal terminal) {
        List<PendingResult> selected = new ArrayList<>();
        for (String id : ids) {
            List<PendingResult> matches = all.stream().filter(r -> r.id().equals(id)).toList();
            if (matches.isEmpty()) {
                matches = all.stream().filter(r -> qualified(root, r).equals(id)).toList();
            }
            if (matches.isEmpty()) {
                terminal.err().println("No pending result for " + id);
                return null;
            }
            if (matches.size() > 1) {
                terminal.err().println(id + " is in several modules, name one of: "
                        + matches.stream().map(r -> qualified(root, r)).collect(Collectors.joining(", ")));
                return null;
            }
            if (!selected.contains(matches.get(0))) {
                selected.add(matches.get(0));
            }
        }
        return all.stream().filter(selected::contains).toList();
    }

    /**
     * How to name a result: its id, or {@code <module>:<id>} when another result has the same id.
     */
    static String name(Path root, PendingResult result, List<PendingResult> all) {
        boolean shared = all.stream().anyMatch(r -> r != result && r.id().equals(result.id()));
        return shared ? qualified(root, result) : result.id();
    }

    private static String qualified(Path root, PendingResult result) {
        String module = root.toAbsolutePath().normalize().relativize(result.moduleDir()).toString();
        return (module.isEmpty() ? "." : module) + ":" + result.id();
    }
}
