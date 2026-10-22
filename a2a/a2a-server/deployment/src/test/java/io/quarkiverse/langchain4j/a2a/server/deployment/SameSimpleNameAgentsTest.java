package io.quarkiverse.langchain4j.a2a.server.deployment;

import static io.quarkiverse.langchain4j.a2a.server.deployment.JsonRpc.sendMessage;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import jakarta.enterprise.context.ApplicationScoped;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.a2a.server.ExposeA2AAgent;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Agents are often declared as member interfaces of a configuration class, so two of them can share a simple name
 * and a package. The executor generated for each is named after the full name of its target, otherwise the second
 * would overwrite the first.
 */
public class SameSimpleNameAgentsTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest().withApplicationRoot(jar -> jar
            .addClasses(JsonRpc.class, FakeChatModel.class, WeatherConfig.class, WeatherConfig.Agent.class,
                    WeatherConfig.Model.class, StoryConfig.class, StoryConfig.Agent.class, StoryConfig.Model.class))
            .overrideRuntimeConfigKey("a2a.authorization.required", "false");

    public static class WeatherConfig {

        public static class Model extends FakeChatModel {

            @Override
            protected String answer(ChatRequest request) {
                return "weather answer";
            }
        }

        @ApplicationScoped
        @RegisterAiService(chatLanguageModelSupplier = Model.class)
        @ExposeA2AAgent(tenant = "weather", skills = @ExposeA2AAgent.Skill(id = "forecast", name = "Forecast", description = "Forecasts the weather", tags = "weather"))
        public interface Agent {

            String chat(@UserMessage String question);
        }
    }

    public static class StoryConfig {

        public static class Model extends FakeChatModel {

            @Override
            protected String answer(ChatRequest request) {
                return "story answer";
            }
        }

        @ApplicationScoped
        @RegisterAiService(chatLanguageModelSupplier = Model.class)
        @ExposeA2AAgent(tenant = "stories", skills = @ExposeA2AAgent.Skill(id = "write_story", name = "Write a story", description = "Writes stories", tags = "writing"))
        public interface Agent {

            String chat(@UserMessage String question);
        }
    }

    @Test
    public void testBothAgentsAnswerUnderTheirOwnTenant() {
        sendMessage("hello").tenant("weather").post()
                .body("error", nullValue())
                .body("result.task.artifacts[0].parts[0].text", equalTo("weather answer"));
        sendMessage("hello").tenant("stories").post()
                .body("error", nullValue())
                .body("result.task.artifacts[0].parts[0].text", equalTo("story answer"));
    }
}
