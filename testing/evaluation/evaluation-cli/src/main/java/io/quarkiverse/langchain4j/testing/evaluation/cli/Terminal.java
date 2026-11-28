package io.quarkiverse.langchain4j.testing.evaluation.cli;

import java.io.PrintWriter;

/**
 * What the commands need from the console, so tests can stand in for a person.
 */
interface Terminal {

    /**
     * Whether a person can answer prompts.
     *
     * @return true when there is a terminal to read from
     */
    boolean interactive();

    /**
     * Show a prompt and read the answer.
     *
     * @param prompt the text to show
     * @return the line typed, or {@code null} at end of input
     */
    String readLine(String prompt);

    PrintWriter out();

    PrintWriter err();
}
