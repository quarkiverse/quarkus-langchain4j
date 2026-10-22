package io.quarkiverse.langchain4j.a2a.server.deployment;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.util.function.Consumer;

import jakarta.enterprise.inject.Instance;

import org.a2aproject.sdk.server.PublicAgentCard;
import org.a2aproject.sdk.spec.AgentCard;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.a2a.server.ExposeA2AAgent;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * The A2A routes live under {@code quarkus.http.root-path}, so the URL on the card has to include it, or a client
 * dialling the card would reach the root of the server instead of the agent.
 */
public class RootPathCardUrlTest {

    @RegisterExtension
    static final QuarkusExtensionTest test = new QuarkusExtensionTest()
            .withApplicationRoot(new Consumer<JavaArchive>() {
                @Override
                public void accept(JavaArchive jar) {
                    jar.addClasses(TestAgent.class, FakeChatModel.class, SunnyChatModel.class);
                }
            })
            .overrideConfigKey("quarkus.http.root-path", "/api")
            .overrideRuntimeConfigKey("quarkus.langchain4j.a2a.server.name", "Weather Agent");

    @PublicAgentCard
    Instance<AgentCard> agentCardInstance;

    @RegisterAiService(chatLanguageModelSupplier = SunnyChatModel.class)
    @ExposeA2AAgent(skills = @ExposeA2AAgent.Skill(id = "weather_search", name = "Search weather", description = "Helps with weather", tags = "weather"))
    public interface TestAgent {

        String chat(@UserMessage String question);
    }

    @Test
    public void testTheCardUrlIncludesTheRootPath() {
        String url = agentCardInstance.get().supportedInterfaces().get(0).url();
        assertThat(url).endsWith("/api");

        given().basePath("")
                .when().get(url + "/.well-known/agent-card.json")
                .then().statusCode(200)
                .body("name", equalTo("Weather Agent"));
    }
}
