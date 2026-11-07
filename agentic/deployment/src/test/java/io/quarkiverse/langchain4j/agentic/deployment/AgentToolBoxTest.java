package io.quarkiverse.langchain4j.agentic.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.declarative.ToolsSupplier;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.ToolBox;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

public class AgentToolBoxTest extends OpenAiBaseTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(CalculatorTool.class, ToolBoxAgent.class, ToolCallModel.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "whatever")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    @Singleton
    public static class CalculatorTool {

        @Tool("Add two numbers")
        public double add(double a, double b) {
            return a + b;
        }
    }

    public static class ToolCallModel implements ChatModel {

        @Override
        public ChatResponse doChat(ChatRequest request) {
            boolean hasToolResult = request.messages().stream()
                    .anyMatch(m -> m instanceof ToolExecutionResultMessage);
            if (hasToolResult) {
                return ChatResponse.builder()
                        .aiMessage(new AiMessage("The result is 42.0"))
                        .build();
            }
            ToolExecutionRequest toolRequest = ToolExecutionRequest.builder()
                    .id("call_1")
                    .name("add")
                    .arguments("{\"a\": 25.0, \"b\": 17.0}")
                    .build();
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from(toolRequest))
                    .build();
        }
    }

    public interface ToolBoxAgent {

        @UserMessage("Use your tools to fulfill: {{request}}.")
        @Agent(description = "An assistant with tools from @ToolBox", outputKey = "answer")
        @ToolBox(CalculatorTool.class)
        String assist(@V("request") String request);

        @ChatModelSupplier
        static ChatModel chatModel() {
            return new ToolCallModel();
        }
    }

    @Inject
    ToolBoxAgent toolBoxAgent;

    @Test
    void agent_should_use_tools_from_toolbox_annotation() {
        String result = toolBoxAgent.assist("What is 25 + 17?");
        assertThat(result).isEqualTo("The result is 42.0");
    }

    public interface ToolSupplierAgent {

        @UserMessage("Use your tools to fulfill: {{request}}.")
        @Agent(description = "An assistant with tools from @ToolBox", outputKey = "answer")
        String assist(@V("request") String request);

        @ChatModelSupplier
        static ChatModel chatModel() {
            return new ToolCallModel();
        }

        @ToolsSupplier
        static Object tools() {
            return new CalculatorTool();
        }
    }

    @Inject
    ToolSupplierAgent toolSupplierAgent;

    @Test
    void agent_should_use_tools_from_toolsupplier_annotation() {
        String result = toolSupplierAgent.assist("What is 25 + 17?");
        assertThat(result).isEqualTo("The result is 42.0");
    }
}
