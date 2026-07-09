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
 * Guards that each root agent's execution report is scoped to that root, so invoking one agentic
 * system does not leak its executions into another system's report.
 */
public class AgenticDevUiMultiSystemTest extends DevUIJsonRPCTest {

    @RegisterExtension
    static final QuarkusDevModeTest devMode = new QuarkusDevModeTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(Consumer.class, EchoAgent.class, UpperAgent.class,
                            EchoSystem.class, UpperSystem.class, EchoResponseChatModel.class)
                    .addAsResource(new StringAsset(String.join("\n",
                            "quarkus.wiremock.devservices.reload=false",
                            "quarkus.langchain4j.openai.api-key=echo",
                            "quarkus.langchain4j.openai.base-url=http://localhost:${quarkus.wiremock.devservices.port}/v1")),
                            "application.properties"));

    public AgenticDevUiMultiSystemTest() {
        super("quarkus-langchain4j-agentic", "http://localhost:8080");
    }

    @Test
    void executionReportIsScopedToTheSelectedRoot() throws Exception {
        JsonNode roots = executeJsonRPCMethod("getRootAgentEntries", Map.of());
        Assertions.assertThat(roots).hasSize(2);

        int echoIndex = indexOf(roots, "EchoSystem");
        int upperIndex = indexOf(roots, "UpperSystem");

        JsonNode invoke = executeJsonRPCMethod("invokeAgent", Map.of(
                "agentClassName", EchoSystem.class.getName(),
                "methodName", "run",
                "inputJson", "{\"text\":\"hello\"}"));
        Assertions.assertThat(invoke.get("success").asBoolean()).isTrue();

        JsonNode echoReport = executeJsonRPCMethod("getExecutionReportJson", Map.of("index", echoIndex));
        Assertions.assertThat(echoReport.get("executions")).isNotEmpty();

        JsonNode upperReport = executeJsonRPCMethod("getExecutionReportJson", Map.of("index", upperIndex));
        Assertions.assertThat(upperReport.get("executions")).isEmpty();
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
        EchoSystem echoSystem;
        @Inject
        UpperSystem upperSystem;
    }

    public interface EchoAgent {

        @UserMessage("{{text}}")
        @Agent(description = "An agent echoing its input", outputKey = "echo")
        String echo(String text);

        @ChatModelSupplier
        static ChatModel chatModel() {
            return new EchoResponseChatModel();
        }
    }

    public interface UpperAgent {

        @UserMessage("{{text}}")
        @Agent(description = "An agent upper-casing its input", outputKey = "upper")
        String upper(String text);

        @ChatModelSupplier
        static ChatModel chatModel() {
            return new EchoResponseChatModel();
        }
    }

    public interface EchoSystem {

        @SequenceAgent(outputKey = "echo", subAgents = { EchoAgent.class })
        String run(String text);
    }

    public interface UpperSystem {

        @SequenceAgent(outputKey = "upper", subAgents = { UpperAgent.class })
        String run(String text);
    }

    public static class EchoResponseChatModel implements ChatModel {

        @Override
        public ChatResponse doChat(ChatRequest request) {
            return ChatResponse.builder().aiMessage(new AiMessage("echo")).build();
        }
    }
}
