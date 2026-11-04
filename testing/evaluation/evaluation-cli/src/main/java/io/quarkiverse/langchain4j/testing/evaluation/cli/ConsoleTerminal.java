package io.quarkiverse.langchain4j.testing.evaluation.cli;

import java.io.PrintWriter;

/**
 * The process console. Piped input has no {@link System#console()}, so it is not interactive.
 */
final class ConsoleTerminal implements Terminal {

    private final PrintWriter out = new PrintWriter(System.out, true);
    private final PrintWriter err = new PrintWriter(System.err, true);

    @Override
    public boolean interactive() {
        java.io.Console console = System.console();
        return console != null && isTerminal(console);
    }

    /**
     * Since JDK 22, {@link System#console()} is non-null even when input or output is redirected, and
     * {@code Console.isTerminal()} tells the difference. It is looked up reflectively because this module compiles
     * for Java 17. On older JDKs a non-null console already means a terminal.
     */
    private static boolean isTerminal(java.io.Console console) {
        try {
            return (boolean) java.io.Console.class.getMethod("isTerminal").invoke(console);
        } catch (NoSuchMethodException e) {
            return true;
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    @Override
    public String readLine(String prompt) {
        out.flush();
        return System.console().readLine("%s", prompt);
    }

    @Override
    public PrintWriter out() {
        return out;
    }

    @Override
    public PrintWriter err() {
        return err;
    }
}
