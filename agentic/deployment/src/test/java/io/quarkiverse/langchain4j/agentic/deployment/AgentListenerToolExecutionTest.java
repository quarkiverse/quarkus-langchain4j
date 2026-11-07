package io.quarkiverse.langchain4j.agentic.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.AgentListenerSupplier;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.declarative.ToolsSupplier;
import dev.langchain4j.agentic.observability.AfterAgentToolExecution;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentRequest;
import dev.langchain4j.agentic.observability.AgentResponse;
import dev.langchain4j.agentic.observability.BeforeAgentToolExecution;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

public class AgentListenerToolExecutionTest extends OpenAiBaseTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(CalculatorTool.class, ToolAgent.class,
                            ToolCallChatModel.class, RecordingAgentListener.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "whatever")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    public static class CalculatorTool {

        @Tool("Add two numbers")
        public double add(double a, double b) {
            return a + b;
        }
    }

    public static class ToolCallChatModel implements ChatModel {

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

    public static class RecordingAgentListener implements AgentListener {

        static final List<String> EVENTS = Collections.synchronizedList(new ArrayList<>());

        static void reset() {
            EVENTS.clear();
        }

        @Override
        public void beforeAgentInvocation(AgentRequest agentRequest) {
            EVENTS.add("beforeAgent:" + agentRequest.agentName());
        }

        @Override
        public void afterAgentInvocation(AgentResponse agentResponse) {
            EVENTS.add("afterAgent:" + agentResponse.agentName());
        }

        @Override
        public void beforeAgentToolExecution(BeforeAgentToolExecution event) {
            EVENTS.add("beforeTool:" + event.toolExecution().request().name());
        }

        @Override
        public void afterAgentToolExecution(AfterAgentToolExecution event) {
            EVENTS.add("afterTool:" + event.toolExecution().request().name());
        }

        @Override
        public boolean inheritedBySubagents() {
            return true;
        }
    }

    public interface ToolAgent {

        @UserMessage("Use your tools to fulfill: {{request}}.")
        @Agent(description = "An assistant with tools", outputKey = "answer")
        String assist(@V("request") String request);

        @ToolsSupplier
        static Object tools() {
            return new CalculatorTool();
        }

        @ChatModelSupplier
        static ChatModel chatModel() {
            return new ToolCallChatModel();
        }

        @AgentListenerSupplier
        static AgentListener listener() {
            return new RecordingAgentListener();
        }
    }

    @Inject
    ToolAgent toolAgent;

    @Test
    void agent_listener_should_receive_tool_execution_callbacks() {
        RecordingAgentListener.reset();

        String result = toolAgent.assist("What is 25 + 17?");
        assertThat(result).isEqualTo("The result is 42.0");

        assertThat(RecordingAgentListener.EVENTS)
                .as("beforeAgentInvocation should be called")
                .anyMatch(e -> e.startsWith("beforeAgent:"));
        assertThat(RecordingAgentListener.EVENTS)
                .as("afterAgentInvocation should be called")
                .anyMatch(e -> e.startsWith("afterAgent:"));

        // These two assertions reproduce the bug: the tool execution callbacks are never fired
        assertThat(RecordingAgentListener.EVENTS)
                .as("beforeAgentToolExecution should be called but is not (issue #2556)")
                .anyMatch(e -> e.equals("beforeTool:add"));
        assertThat(RecordingAgentListener.EVENTS)
                .as("afterAgentToolExecution should be called but is not (issue #2556)")
                .anyMatch(e -> e.equals("afterTool:add"));
    }
}
