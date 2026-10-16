package io.quarkiverse.langchain4j.a2a.server.deployment;

import static io.quarkiverse.langchain4j.a2a.server.deployment.JsonRpc.sendMessage;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.a2a.server.ExposeA2AAgent;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

/**
 * A structured result must reach the client as a data part carrying its JSON form, which is the half of the
 * mapping that inspecting the Agent Card cannot show.
 */
public class StructuredResultOverJsonRpcTest extends OpenAiBaseTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest().withApplicationRoot(jar -> jar
            .addClasses(JsonRpc.class, TestAgent.class, Forecast.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "whatever")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"))
            .overrideRuntimeConfigKey("quarkus.langchain4j.a2a.server.name", "Weather Agent")
            .overrideRuntimeConfigKey("a2a.authorization.required", "false");

    public record Forecast(String city, int temperature) {
    }

    @RegisterAiService
    @ExposeA2AAgent(skills = @ExposeA2AAgent.Skill(id = "forecast", name = "Forecast", description = "Structured forecast", tags = "weather"))
    public interface TestAgent {

        Forecast chat(@UserMessage String question);
    }

    @Test
    public void testStructuredResultTravelsAsADataPart() {
        // the stub substitutes this straight into a JSON string, so the quotes have to survive as escapes
        setChatCompletionMessageContent("{\\\"city\\\":\\\"LA\\\",\\\"temperature\\\":25}");

        sendMessage("weather in LA?").post()
                .body("error", nullValue())
                .body("result.task.artifacts[0].parts[0].data.city", equalTo("LA"))
                // the SDK parses JSON numbers into floats
                .body("result.task.artifacts[0].parts[0].data.temperature", equalTo(25.0f))
                .body("result.task.status.state", equalTo("TASK_STATE_COMPLETED"));
    }
}
