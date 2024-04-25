//DEPS io.quarkus.platform:quarkus-bom:3.9.4@pom
//DEPS io.quarkus:quarkus-vertx-http:3.9.4
//DEPS io.quarkiverse.langchain4j:quarkus-langchain4j-openai:0.12.0

import dev.langchain4j.model.chat.ChatLanguageModel;
import io.vertx.ext.web.Router;
import jakarta.enterprise.event.Observes;

public class jokes {
    void route(@Observes Router router, ChatLanguageModel ai) {
        router.get("/joke").blockingHandler(rc -> rc.end(ai.generate("tell me a joke")));
    }
}