package io.quarkiverse.langchain4j.agentic.devmode;

import java.util.Map;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.assertj.core.api.Assertions;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.JsonNode;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.UserMessage;
import io.quarkus.devui.tests.DevUIJsonRPCTest;
import io.quarkus.test.QuarkusDevModeTest;

/**
 * Guards root attribution when two agentic systems share the same sub-agent type. Type-based
 * scoping alone cannot tell the runs apart, so the execution report must attribute each run to
 * the root that started it via its memoryId.
 */
public class AgenticDevUiSharedSubAgentTest extends DevUIJsonRPCTest {

    @RegisterExtension
    static final QuarkusDevModeTest devMode = new QuarkusDevModeTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(Consumer.class, SharedAgent.class, SystemOne.class, SystemTwo.class,
                            EchoResponseChatModel.class)
                    .addAsResource(new StringAsset(String.join("\n",
                            "quarkus.wiremock.devservices.reload=false",
                            "quarkus.langchain4j.openai.api-key=echo",
                            "quarkus.langchain4j.openai.base-url=http://localhost:${quarkus.wiremock.devservices.port}/v1")),
                            "application.properties"));

    public AgenticDevUiSharedSubAgentTest() {
        super("quarkus-langchain4j-agentic", "http://localhost:8080");
    }

    @Test
    void sharedSubAgentIsAttributedToTheInvokedRoot() throws Exception {
        JsonNode roots = executeJsonRPCMethod("getRootAgentEntries", Map.of());
        Assertions.assertThat(roots).hasSize(2);

        int oneIndex = indexOf(roots, "SystemOne");
        int twoIndex = indexOf(roots, "SystemTwo");

        JsonNode invoke = executeJsonRPCMethod("invokeAgent", Map.of(
                "agentClassName", SystemOne.class.getName(),
                "methodName", "run",
                "inputJson", "{\"text\":\"hello\"}"));
        Assertions.assertThat(invoke.get("success").asBoolean()).isTrue();

        JsonNode oneReport = executeJsonRPCMethod("getExecutionReportJson", Map.of("index", oneIndex));
        Assertions.assertThat(oneReport.get("executions")).isNotEmpty();

        JsonNode twoReport = executeJsonRPCMethod("getExecutionReportJson", Map.of("index", twoIndex));
        Assertions.assertThat(twoReport.get("executions")).isEmpty();
    }

    private static int indexOf(JsonNode roots, String name) {
        for (JsonNode entry : roots) {
            if (name.equals(entry.get("name").asText())) {
                return entry.get("index").asInt();
            }
        }
        throw new AssertionError("Root agent not found: " + name);
    }

    @Singleton
    public static class Consumer {
        @Inject
        SystemOne systemOne;
        @Inject
        SystemTwo systemTwo;
    }

    public interface SharedAgent {

        @UserMessage("{{text}}")
        @Agent(description = "An agent echoing its input", outputKey = "echo")
        String echo(String text);

        @ChatModelSupplier
        static ChatModel chatModel() {
            return new EchoResponseChatModel();
        }
    }

    public interface SystemOne {

        @SequenceAgent(outputKey = "echo", subAgents = { SharedAgent.class })
        String run(String text);
    }

    public interface SystemTwo {

        @SequenceAgent(outputKey = "echo", subAgents = { SharedAgent.class })
        String run(String text);
    }

    public static class EchoResponseChatModel implements ChatModel {

        @Override
        public ChatResponse doChat(ChatRequest request) {
            return ChatResponse.builder().aiMessage(new AiMessage("echo")).build();
        }
    }
}
