///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS io.quarkus.platform:quarkus-bom:3.27.2@pom
//DEPS io.quarkus:quarkus-picocli
//DEPS io.quarkiverse.langchain4j:quarkus-langchain4j-openai:1.4.2
//Q:CONFIG quarkus.banner.enabled=false
//Q:CONFIG quarkus.log.level=WARN
import dev.langchain4j.model.chat.ChatModel;
import jakarta.inject.Inject;
import picocli.CommandLine.Command;

@Command
public class jokes implements Runnable {
    @Inject
    private ChatModel ai;

    @Override
    public void run() {
        System.out.println(ai.generate("tell me a joke"));
    }
}
