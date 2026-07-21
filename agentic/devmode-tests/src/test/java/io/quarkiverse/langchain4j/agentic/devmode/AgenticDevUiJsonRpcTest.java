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
 * Guards the Dev UI JSON-RPC service consolidation: a single dev-only service is live, its JSON
 * report methods resolve, and the invokeAgent allow-list rejects non-agent classes.
 */
public class AgenticDevUiJsonRpcTest extends DevUIJsonRPCTest {

    @RegisterExtension
    static final QuarkusDevModeTest devMode = new QuarkusDevModeTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(AgentConsumer.class, EchoAgent.class, SequenceWrapper.class, EchoResponseChatModel.class)
                    .addAsResource(new StringAsset(String.join("\n",
                            "quarkus.wiremock.devservices.reload=false",
                            "quarkus.langchain4j.openai.api-key=echo",
                            "quarkus.langchain4j.openai.base-url=http://localhost:${quarkus.wiremock.devservices.port}/v1")),
                            "application.properties"));

    public AgenticDevUiJsonRpcTest() {
        super("quarkus-langchain4j-agentic", "http://localhost:8080");
    }

    @Test
    void monitoringPopulatesTopologyAndExecutions() throws Exception {
        JsonNode roots = executeJsonRPCMethod("getRootAgentEntries", Map.of());
        Assertions.assertThat(roots).isNotNull();
        Assertions.assertThat(roots.isArray()).isTrue();
        Assertions.assertThat(roots).isNotEmpty();

        JsonNode invoke = executeJsonRPCMethod("invokeAgent", Map.of(
                "agentClassName", SequenceWrapper.class.getName(),
                "methodName", "run",
                "inputJson", "{\"text\":\"hello\"}"));
        Assertions.assertThat(invoke.get("success").asBoolean()).isTrue();

        JsonNode report = executeJsonRPCMethod("getExecutionReportJson", Map.of("index", 0));
        Assertions.assertThat(report.get("executions")).isNotEmpty();
    }

    @Test
    void invokeAgentRejectsNonAgentClass() throws Exception {
        JsonNode response = executeJsonRPCMethod("invokeAgent", Map.of(
                "agentClassName", "java.lang.String",
                "methodName", "toString",
                "inputJson", "{}"));
        Assertions.assertThat(response.get("success").asBoolean()).isFalse();
        Assertions.assertThat(response.get("error").asText()).contains("Unknown agent class");
    }

    @Singleton
    public static class AgentConsumer {
        @Inject
        SequenceWrapper sequenceWrapper;
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

    public interface SequenceWrapper {

        @SequenceAgent(outputKey = "echo", subAgents = { EchoAgent.class })
        String run(String text);
    }

    public static class EchoResponseChatModel implements ChatModel {

        @Override
        public ChatResponse doChat(ChatRequest request) {
            return ChatResponse.builder().aiMessage(new AiMessage("echo")).build();
        }
    }
}
