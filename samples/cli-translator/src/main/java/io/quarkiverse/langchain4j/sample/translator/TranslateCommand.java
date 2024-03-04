package io.quarkiverse.langchain4j.sample.translator;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "translate", mixinStandardHelpOptions = true)
@ActivateRequestContext
public class TranslateCommand implements Runnable {

    @Parameters(paramLabel = "<filename>", description = "File to translate.")
    String filename;

    @Option(names = { "-l", "--language" }, description = "Translate to language", defaultValue = "English")
    String language;

    @Inject
    TranslatorAiService translator;

    @Override
    public void run() {
        File file = new File(filename);

        try (Scanner scanner = new Scanner(file)) {
            // Use a regular expression to define paragraph-ending delimiters.
            // This pattern matches two or more newline characters, indicating a paragraph break.
            scanner.useDelimiter("\\n{2,}");

            while (scanner.hasNext()) {
                String paragraph = scanner.next();
                // And here's where the paragraph translation happens
                String translation = translator.translate(paragraph.trim(), language);
                System.out.println(translation);
                System.out.println(); // Add an empty line between paragraphs for clarity
            }
        } catch (FileNotFoundException e) {
            System.err.println("File not found: " + filename);
        }
    }
}
