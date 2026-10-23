package io.quarkiverse.langchain4j.a2a.server.deployment;

import static io.quarkiverse.langchain4j.a2a.server.deployment.JsonRpc.sendMessage;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import java.util.List;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

import org.a2aproject.sdk.server.ExtendedAgentCard;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.a2a.server.ExposeA2AAgent;
import io.quarkus.test.QuarkusUnitTest;

/**
 * The specification leaves it to the server what becomes of a request naming a tenant nobody serves. The SDK
 * refuses it unless an extended card is produced without a tenant, which its router falls back to, and the request
 * then reaches the agent exposed without one. {@link MultiTenancyTest} covers the refusal.
 */
public class UnknownTenantWithDefaultExtendedCardTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest().withApplicationRoot(jar -> jar
            .addClasses(JsonRpc.class, FakeChatModel.class, DefaultChatModel.class, WeatherChatModel.class,
                    DefaultAgent.class, WeatherAgent.class, ExtendedCardProducer.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.a2a.server.name", "Default Agent")
            .overrideRuntimeConfigKey("a2a.authorization.required", "false");

    public static class DefaultChatModel extends FakeChatModel {

        @Override
        protected String answer(ChatRequest request) {
            return "default";
        }
    }

    public static class WeatherChatModel extends FakeChatModel {

        @Override
        protected String answer(ChatRequest request) {
            return "weather";
        }
    }

    @RegisterAiService(chatLanguageModelSupplier = DefaultChatModel.class)
    @ExposeA2AAgent(extendedAgentCard = true, skills = @ExposeA2AAgent.Skill(id = "chat", name = "Chat", description = "Chats", tags = "chat"))
    public interface DefaultAgent {

        String chat(@UserMessage String question);
    }

    @RegisterAiService(chatLanguageModelSupplier = WeatherChatModel.class)
    @ExposeA2AAgent(tenant = "weather", skills = @ExposeA2AAgent.Skill(id = "forecast", name = "Forecast", description = "Forecasts the weather", tags = "weather"))
    public interface WeatherAgent {

        String chat(@UserMessage String question);
    }

    @Singleton
    public static class ExtendedCardProducer {

        @Produces
        @ExtendedAgentCard
        public AgentCard extendedCard() {
            return AgentCard.builder()
                    .name("Default Agent (Extended)")
                    .description("The card reserved for known clients")
                    .version("1.0.0")
                    .capabilities(new AgentCapabilities(false, false, true, List.of()))
                    .supportedInterfaces(List.of(new AgentInterface("JSONRPC", "http://localhost:8081", null)))
                    .defaultInputModes(List.of("text/plain"))
                    .defaultOutputModes(List.of("text/plain"))
                    .skills(List.of())
                    .build();
        }
    }

    @Test
    public void testAnUnknownTenantReachesTheAgentWithoutATenant() {
        sendMessage("hello").tenant("wether").post()
                .body("error", nullValue())
                .body("result.task.artifacts[0].parts[0].text", equalTo("default"));
    }

    @Test
    public void testAKnownTenantIsStillServedByItsOwnAgent() {
        sendMessage("hello").tenant("weather").post()
                .body("result.task.artifacts[0].parts[0].text", equalTo("weather"));
    }
}
