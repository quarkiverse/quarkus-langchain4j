package io.quarkiverse.langchain4j.a2a.server.deployment;

import static io.quarkiverse.langchain4j.a2a.server.deployment.JsonRpc.sendMessage;
import static org.hamcrest.Matchers.equalTo;

import jakarta.enterprise.context.ApplicationScoped;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.a2a.server.ExposeA2AAgent;
import io.quarkus.test.QuarkusUnitTest;

/**
 * The {@code contextId} is the client's to choose and chat memories are kept by id in a store the agents share, so a
 * client of the agent without a tenant must not be able to reach a tenant's conversation by composing its memory id
 * into the {@code contextId}.
 */
public class MemoryIdIsolationTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest().withApplicationRoot(jar -> jar
            .addClasses(JsonRpc.class, FakeChatModel.class, DefaultChatModel.class, WeatherChatModel.class,
                    DefaultAgent.class, WeatherAgent.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.a2a.server.name", "Default Agent")
            .overrideRuntimeConfigKey("a2a.authorization.required", "false");

    public static class DefaultChatModel extends FakeChatModel {

        @Override
        protected String answer(ChatRequest request) {
            return "default heard " + userMessageCount(request);
        }
    }

    public static class WeatherChatModel extends FakeChatModel {

        @Override
        protected String answer(ChatRequest request) {
            return "weather heard " + userMessageCount(request);
        }
    }

    @ApplicationScoped
    @RegisterAiService(chatLanguageModelSupplier = DefaultChatModel.class)
    @ExposeA2AAgent(skills = @ExposeA2AAgent.Skill(id = "chat", name = "Chat", description = "Chats", tags = "chat"))
    public interface DefaultAgent {

        String chat(@MemoryId String conversationId, @UserMessage String question);
    }

    @ApplicationScoped
    @RegisterAiService(chatLanguageModelSupplier = WeatherChatModel.class)
    @ExposeA2AAgent(tenant = "weather", skills = @ExposeA2AAgent.Skill(id = "forecast", name = "Forecast", description = "Forecasts the weather", tags = "weather"))
    public interface WeatherAgent {

        String chat(@MemoryId String conversationId, @UserMessage String question);
    }

    @Test
    public void testAContextNamingATenantMemoryDoesNotReachThatConversation() {
        sendMessage("my name is Jane Roe").contextId("isolated").tenant("weather").post()
                .body("result.task.artifacts[0].parts[0].text", equalTo("weather heard 1"));
        sendMessage("what is my name?").contextId("isolated#weather").post()
                .body("result.task.artifacts[0].parts[0].text", equalTo("default heard 1"));
        sendMessage("what is my name?").contextId("isolated").tenant("weather").post()
                .body("result.task.artifacts[0].parts[0].text", equalTo("weather heard 2"));
    }
}
