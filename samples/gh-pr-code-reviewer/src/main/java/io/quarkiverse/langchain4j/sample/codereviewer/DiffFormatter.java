package io.quarkiverse.langchain4j.sample.codereviewer;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.quarkiverse.langchain4j.sample.codereviewer.GitHubPullRequests.FileChange;

/**
 * Prepares a pull request's changes for the model.
 *
 * <p>
 * Two things a model does badly on a raw diff are done here instead:
 * <ul>
 * <li><b>Line numbers.</b> Every added and context line is prefixed with its line number in the new
 * file, so findings can cite {@code file:line} without the model counting from hunk headers.</li>
 * <li><b>Size.</b> The prompt has to fit in the model's context. Lock files, binaries and generated
 * files are dropped, then whole files are included until the budget runs out; the rest are listed as
 * not reviewed.</li>
 * </ul>
 */
final class DiffFormatter {

    private static final Pattern HUNK_HEADER = Pattern.compile("^@@ -\\d+(?:,\\d+)? \\+(\\d+)(?:,\\d+)? @@.*$");
    private static final Pattern SKIPPED = Pattern.compile(
            "(^|/)(package-lock\\.json|yarn\\.lock|pnpm-lock\\.yaml|Cargo\\.lock|go\\.sum|poetry\\.lock|Gemfile\\.lock)$"
                    + "|\\.(min\\.js|min\\.css|map|svg|png|jpe?g|gif|ico|pdf|jar|gguf|bin)$");

    /** The formatted changes, and what was left out of them. */
    record Result(String text, int filesIncluded, List<String> skipped, List<String> overBudget) {
    }

    private DiffFormatter() {
    }

    static Result format(List<FileChange> files, int maxChars) {
        List<String> skipped = new ArrayList<>();
        List<String> overBudget = new ArrayList<>();
        StringBuilder out = new StringBuilder();
        int included = 0;
        for (FileChange file : files) {
            // No patch: GitHub shows none for a binary file or one too large to display
            if (file.patch() == null || SKIPPED.matcher(file.path()).find()) {
                skipped.add(file.path());
                continue;
            }
            String formatted = format(file);
            if (out.length() + formatted.length() > maxChars) {
                overBudget.add(file.path());
                continue;
            }
            out.append(formatted);
            included++;
        }
        return new Result(out.toString(), included, skipped, overBudget);
    }

    private static String format(FileChange file) {
        StringBuilder body = new StringBuilder();
        int newLine = 0;
        for (String line : file.patch().split("\n", -1)) {
            Matcher hunk = HUNK_HEADER.matcher(line);
            if (hunk.matches()) {
                newLine = Integer.parseInt(hunk.group(1));
                body.append(line).append('\n');
            } else if (line.startsWith("+")) {
                body.append(String.format("%5d + %s\n", newLine++, line.substring(1)));
            } else if (line.startsWith("-")) {
                body.append("      - ").append(line.substring(1)).append('\n');
            } else if (line.startsWith(" ")) {
                body.append(String.format("%5d   %s\n", newLine++, line.substring(1)));
            }
            // anything else, such as "\ No newline at end of file", carries no code
        }
        return "### " + file.path() + " (" + status(file) + ")\n" + body + "\n";
    }

    private static String status(FileChange file) {
        return "renamed".equals(file.status()) && file.previousPath() != null
                ? "renamed from " + file.previousPath()
                : file.status();
    }
}
