package io.quarkiverse.langchain4j.sample.translator;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Scanner;
import java.util.concurrent.Callable;

import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "translate", mixinStandardHelpOptions = true, description = "Translate a text file (or stdin) from one language to another.", version = "v1.0")
@ActivateRequestContext
public class TranslateCommand implements Callable<Integer> {

    @Parameters(paramLabel = "filename", arity = "0..1", description = "File to translate.")
    String filename;

    @Option(names = { "-l", "--language" }, description = "Translate to language", defaultValue = "English")
    String targetLanguage;

    @Inject
    TranslatorAiService translator;

    @Override
    public Integer call() {
        Scanner scanner;
        // decide where to read from
        if (filename != null) {
            // a filename is specified on the command line
            try {
                scanner = new Scanner(new File(filename));
            } catch (FileNotFoundException e) {
                System.err.println("File not found: " + filename);
                return 1;
            }
        } else {
            // translate any text piped through stdin
            scanner = new Scanner(System.in);
            try {
                // Check for interactive mode to provide hints
                if (System.in.available() == 0) {
                    System.out.println("Entering interactive mode: type your text in any language.");
                    System.out.println("Leave an empty line to translate it. Type <CTRL-D> to exit.");
                }
            } catch (IOException e) {
                System.err.println("Error reading from stdin: " + e);
            }
        }
        // This pattern matches two or more newline characters, indicating a paragraph break.
        scanner.useDelimiter("\\n{2,}");
        while (scanner.hasNext()) {
            // Translate a paragraph at a time and print it to stdout.
            String paragraph = scanner.next();
            System.out.println(translator.translate(paragraph, targetLanguage));
            System.out.println(); // Add an empty line between paragraphs
        }
        scanner.close();
        return 0;
    }
}
