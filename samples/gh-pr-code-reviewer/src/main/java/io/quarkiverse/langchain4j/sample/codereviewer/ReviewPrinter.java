package io.quarkiverse.langchain4j.sample.codereviewer;

import java.util.function.Consumer;

/**
 * Prints the review as it streams in: the model's reasoning under its own heading, dimmed on a
 * terminal, then the review itself.
 */
final class ReviewPrinter {

    private static final String DIM = "\u001b[2m";
    private static final String RESET = "\u001b[0m";

    private final Consumer<String> out;
    private final boolean ansi;
    private boolean inThinking;
    private boolean thinkingShown;

    ReviewPrinter(Consumer<String> out, boolean ansi) {
        this.out = out;
        this.ansi = ansi;
    }

    void thinking(String text) {
        // The reasoning arrives with its <think> and </think> tags
        String visible = text.replace("<think>", "").replace("</think>", "");
        if (visible.isEmpty()) {
            return;
        }
        if (!inThinking) {
            out.accept((thinkingShown ? "" : "Thinking\n--------\n") + (ansi ? DIM : ""));
            inThinking = true;
            thinkingShown = true;
        }
        out.accept(visible);
    }

    void response(String chunk) {
        if (inThinking) {
            out.accept((ansi ? RESET : "") + "\n\n");
            inThinking = false;
        }
        out.accept(chunk);
    }
}
