package io.quarkiverse.langchain4j.a2a.server.deployment;

import static io.quarkiverse.langchain4j.a2a.server.deployment.JsonRpc.sendMessage;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.a2a.server.ExposeA2AAgent;
import io.quarkus.test.QuarkusUnitTest;

/**
 * A workflow that hands back a structured result wrapped with its scope reaches the client as a data part holding
 * the result alone, which is the half of the mapping that inspecting the Agent Card cannot show.
 */
public class StructuredWorkflowResultTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest().withApplicationRoot(jar -> jar
            .addClasses(JsonRpc.class, FakeChatModel.class, FixedChatModel.class, Forecast.class, CityPicker.class,
                    Forecaster.class, ForecastCreator.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "whatever")
            .overrideRuntimeConfigKey("quarkus.langchain4j.a2a.server.name", "Forecast Agent")
            .overrideRuntimeConfigKey("a2a.authorization.required", "false");

    public record Forecast(String city, int temperature) {
    }

    public static class FixedChatModel extends FakeChatModel {

        private final String answer;

        FixedChatModel(String answer) {
            this.answer = answer;
        }

        @Override
        protected String answer(ChatRequest request) {
            return answer;
        }
    }

    public interface CityPicker {

        @UserMessage("{{topic}}")
        @Agent(description = "Pick a city", outputKey = "city")
        String pick(@V("topic") String topic);

        @ChatModelSupplier
        static ChatModel chatModel() {
            return new FixedChatModel("LA");
        }
    }

    public interface Forecaster {

        @UserMessage("{{city}}")
        @Agent(description = "Forecast the weather of a city", outputKey = "forecast")
        Forecast forecast(@V("city") String city);

        @ChatModelSupplier
        static ChatModel chatModel() {
            return new FixedChatModel("{\"city\":\"LA\",\"temperature\":25}");
        }
    }

    @ExposeA2AAgent(skills = @ExposeA2AAgent.Skill(id = "forecast", name = "Forecast", description = "Forecasts the weather", tags = "weather"))
    public interface ForecastCreator {

        @SequenceAgent(outputKey = "forecast", subAgents = { CityPicker.class, Forecaster.class })
        ResultWithAgenticScope<Forecast> create(@V("topic") String topic);
    }

    @Test
    public void testTheResultTravelsAsADataPart() {
        sendMessage("somewhere sunny").post()
                .body("error", nullValue())
                .body("result.task.status.state", equalTo("TASK_STATE_COMPLETED"))
                .body("result.task.artifacts[0].parts[0].data.city", equalTo("LA"))
                .body("result.task.artifacts[0].parts[0].data.temperature", equalTo(25.0f));
    }
}
