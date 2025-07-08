package io.quarkiverse.langchain4j.sample;

import java.util.concurrent.Callable;

import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "poem", mixinStandardHelpOptions = true, description = "Create a poem", version = "v1.0")
@ActivateRequestContext
public class PoemCommand implements Callable<Integer> {

    @Option(names = { "-l", "--language" }, description = "Poem language", defaultValue = "English")
    String poemLanguage;

    @Inject
    PoemService poemService;

    @Override
    public Integer call() {
        System.out.println(poemService.writePoem(poemLanguage));
        return 0;
    }
}