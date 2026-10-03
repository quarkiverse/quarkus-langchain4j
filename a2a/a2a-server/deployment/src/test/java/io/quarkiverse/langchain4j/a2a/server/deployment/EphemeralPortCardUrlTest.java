package io.quarkiverse.langchain4j.a2a.server.deployment;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.function.Supplier;

import jakarta.enterprise.inject.Instance;

import org.a2aproject.sdk.server.PublicAgentCard;
import org.a2aproject.sdk.spec.AgentCard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.a2a.server.ExposeA2AAgent;
import io.quarkus.test.QuarkusUnitTest;

/**
 * With {@code quarkus.http.port} set to {@code 0} the operating system chooses the port, and the options Quarkus
 * publishes with {@code HttpServerStart} still carry the configured one, which it stores as {@code -1}. The card
 * has to name the port the server actually bound, because a client that cannot dial the address it was given has
 * no other way of reaching the agent.
 */
public class EphemeralPortCardUrlTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest().withApplicationRoot(jar -> jar
            .addClasses(TestAgent.class, SilentChatModelSupplier.class))
            .overrideConfigKey("quarkus.http.test-port", "0")
            .overrideRuntimeConfigKey("quarkus.langchain4j.a2a.server.name", "Weather Agent");

    @PublicAgentCard
    Instance<AgentCard> agentCardInstance;

    /**
     * The card is what this test is about, so the agent is given a model of its own and the application needs no
     * provider configuration.
     */
    public static class SilentChatModelSupplier implements Supplier<ChatModel> {

        @Override
        public ChatModel get() {
            return new ChatModel() {
                @Override
                public ChatResponse doChat(ChatRequest request) {
                    return ChatResponse.builder().aiMessage(new AiMessage("It is sunny.")).build();
                }
            };
        }
    }

    @RegisterAiService(chatLanguageModelSupplier = SilentChatModelSupplier.class)
    @ExposeA2AAgent(skills = @ExposeA2AAgent.Skill(id = "weather_search", name = "Search weather", description = "Helps with weather", tags = "weather"))
    public interface TestAgent {

        String chat(@UserMessage String question);
    }

    @Test
    public void testTheCardNamesTheBoundPort() {
        String url = agentCardInstance.get().supportedInterfaces().get(0).url();
        assertThat(url).doesNotContain(":-1");

        URI address = URI.create(url);
        assertThat(address.getPort()).isPositive();

        // the port is only demonstrably the right one if the card can be fetched at it
        given().baseUri("http://" + address.getHost()).port(address.getPort())
                .when().get("/.well-known/agent-card.json")
                .then().statusCode(200);
    }
}
