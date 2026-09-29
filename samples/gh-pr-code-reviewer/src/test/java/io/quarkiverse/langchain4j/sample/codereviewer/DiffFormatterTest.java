package io.quarkiverse.langchain4j.sample.codereviewer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.quarkiverse.langchain4j.sample.codereviewer.GitHubPullRequests.FileChange;

class DiffFormatterTest {

    private static final List<FileChange> FILES = List.of(
            new FileChange("src/Main.java", "modified", null, """
                    @@ -10,3 +10,4 @@ class Main {
                         int a = 1;
                    -    int b = 2;
                    +    int b = 3;
                    +    int c = 4;
                         return;
                    \\ No newline at end of file"""),
            new FileChange("NEW.md", "added", null, """
                    @@ -0,0 +1 @@
                    +hello"""),
            new FileChange("src/Renamed.java", "renamed", "src/Old.java", """
                    @@ -1 +1 @@
                    -class Old {}
                    +class Renamed {}"""),
            new FileChange("package-lock.json", "modified", null, """
                    @@ -1 +1 @@
                    -{}
                    +{ }"""),
            // GitHub gives no patch for a binary file or one too large to show
            new FileChange("logo.png", "modified", null, null));

    @Test
    void numbersAddedAndUnchangedLinesByTheirNewFileLine() {
        String text = DiffFormatter.format(FILES, 10_000).text();

        assertTrue(text.contains("### src/Main.java (modified)\n"), text);
        assertTrue(text.contains("   10       int a = 1;\n"), text);
        assertTrue(text.contains("      -     int b = 2;\n"), text);
        assertTrue(text.contains("   11 +     int b = 3;\n"), text);
        assertTrue(text.contains("   12 +     int c = 4;\n"), text);
        assertTrue(text.contains("   13       return;\n"), text);
        assertFalse(text.contains("No newline"), text);
    }

    @Test
    void reportsTheStatusOfEachFile() {
        String text = DiffFormatter.format(FILES, 10_000).text();

        assertTrue(text.contains("### NEW.md (added)\n"), text);
        assertTrue(text.contains("    1 + hello\n"), text);
        assertTrue(text.contains("### src/Renamed.java (renamed from src/Old.java)\n"), text);
    }

    @Test
    void skipsLockFilesAndFilesWithoutAPatch() {
        DiffFormatter.Result result = DiffFormatter.format(FILES, 10_000);

        assertEquals(3, result.filesIncluded());
        assertEquals(List.of("package-lock.json", "logo.png"), result.skipped());
        assertEquals(List.of(), result.overBudget());
    }

    @Test
    void leavesOutWholeFilesThatDoNotFitTheBudget() {
        DiffFormatter.Result result = DiffFormatter.format(FILES, 80);

        assertEquals(List.of("src/Main.java", "src/Renamed.java"), result.overBudget());
        assertTrue(result.text().startsWith("### NEW.md"), result.text());
    }
}
