package io.quarkiverse.langchain4j.a2a.server.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Supplier;

import org.eclipse.microprofile.config.ConfigProvider;
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
 * The extension turns the SDK's fail-closed task authorization off, because it exposes one agent with no notion of
 * task ownership and the denial would surface as {@code TaskNotFoundError}. Relaxing a security default is a
 * decision that belongs to the application that asked for an agent, and to no one else — the other half of that is
 * pinned in {@link NoAuthorizationRelaxationWithoutAgentTest}.
 */
public class AuthorizationDefaultTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest().withApplicationRoot(jar -> jar
            .addClasses(TestAgent.class, SilentChatModelSupplier.class));

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
    public void testTheCheckIsOffForAnApplicationThatExposesAnAgent() {
        assertThat(ConfigProvider.getConfig().getOptionalValue("a2a.authorization.required", Boolean.class))
                .contains(false);
    }
}
