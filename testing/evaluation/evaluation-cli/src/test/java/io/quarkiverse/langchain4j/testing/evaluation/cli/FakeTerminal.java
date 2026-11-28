package io.quarkiverse.langchain4j.testing.evaluation.cli;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

final class FakeTerminal implements Terminal {

    private final boolean interactive;
    private final Deque<String> answers = new ArrayDeque<>();
    final List<String> prompts = new ArrayList<>();
    private final StringWriter out = new StringWriter();
    private final StringWriter err = new StringWriter();

    FakeTerminal(boolean interactive, String... answers) {
        this.interactive = interactive;
        this.answers.addAll(List.of(answers));
    }

    static FakeTerminal none() {
        return new FakeTerminal(false);
    }

    @Override
    public boolean interactive() {
        return interactive;
    }

    @Override
    public String readLine(String prompt) {
        prompts.add(prompt);
        return answers.poll();
    }

    @Override
    public PrintWriter out() {
        return new PrintWriter(out, true);
    }

    @Override
    public PrintWriter err() {
        return new PrintWriter(err, true);
    }

    String output() {
        return out.toString();
    }

    String errors() {
        return err.toString();
    }
}
