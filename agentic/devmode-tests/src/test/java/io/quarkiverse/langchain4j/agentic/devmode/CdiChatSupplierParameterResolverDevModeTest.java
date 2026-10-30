package io.quarkiverse.langchain4j.agentic.devmode;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.agentic.runtime.CdiBean;
import io.quarkus.test.QuarkusDevModeTest;

public class CdiChatSupplierParameterResolverDevModeTest {

    static final int HTTP_PORT = findAvailablePort();

    @RegisterExtension
    static final QuarkusDevModeTest devModeTest = new QuarkusDevModeTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class)
                            .addClasses(
                                    AgentResource.class,
                                    ModelSelector.class, ModelProducer.class,
                                    EchoAgent.class, ModelSelectingAgent.class, SequenceWrapper.class,
                                    MixedParamsAgent.class, MixedParamsSequenceWrapper.class,
                                    FixedResponseChatModel.class, EchoResponseChatModel.class)
                            .addAsResource(new StringAsset(String.join("\n",
                                    "quarkus.http.port=" + HTTP_PORT,
                                    "quarkus.wiremock.devservices.reload=false",
                                    "quarkus.langchain4j.openai.echo.api-key=echo",
                                    "quarkus.langchain4j.openai.fixed.api-key=fixed",
                                    "quarkus.langchain4j.openai.base-url=http://localhost:${quarkus.wiremock.devservices.port}/v1")),
                                    "application.properties"));

    static final String SELECTED_RESPONSE = "selected-by-cdi-bean";

    @Test
    void cdiResolvedBeanIsUsedInChatModelSupplierInDevMode() {
        String result = given()
                .port(HTTP_PORT)
                .get("/agent/cdi")
                .then()
                .statusCode(200)
                .extract()
                .asString();

        assertEquals(SELECTED_RESPONSE, result);
    }

    @Test
    void mixedScopeAndCdiParamsWorkInDevMode() {
        String result = given()
                .port(HTTP_PORT)
                .get("/agent/mixed")
                .then()
                .statusCode(200)
                .extract()
                .asString();

        assertEquals(SELECTED_RESPONSE, result);
    }

    @Path("/agent")
    public static class AgentResource {

        @Inject
        SequenceWrapper sequenceWrapper;

        @Inject
        MixedParamsSequenceWrapper mixedParamsSequenceWrapper;

        @GET
        @Path("/cdi")
        public String cdi() {
            return sequenceWrapper.run("cat");
        }

        @GET
        @Path("/mixed")
        public String mixed() {
            return mixedParamsSequenceWrapper.run("cat");
        }
    }

    @Singleton
    public static class ModelSelector {

        @ModelName("fixed")
        ChatModel fixed;

        @ModelName("echo")
        ChatModel echo;

        public ChatModel select(String input) {
            if (input.contains("a")) {
                return fixed;
            }
            return echo;
        }
    }

    @Singleton
    public static class ModelProducer {

        @Produces
        public ChatModel defaultModel() {
            return new EchoResponseChatModel();
        }

        @Produces
        @ModelName("fixed")
        public ChatModel fixed() {
            return new FixedResponseChatModel(SELECTED_RESPONSE);
        }

        @Produces
        @ModelName("echo")
        public ChatModel echo() {
            return new EchoResponseChatModel();
        }
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

    public interface ModelSelectingAgent {

        @UserMessage("Answer: {{text}}")
        @Agent(description = "An agent using a CDI-resolved model selector", outputKey = "answer")
        String answer(String text);

        @ChatModelSupplier
        static ChatModel chatModel(@CdiBean ModelSelector selector, String echo) {
            return selector.select(echo);
        }
    }

    public interface SequenceWrapper {

        @SequenceAgent(outputKey = "answer", subAgents = { EchoAgent.class, ModelSelectingAgent.class })
        String run(String text);
    }

    public interface MixedParamsAgent {

        @UserMessage("Answer: {{text}}")
        @Agent(description = "An agent mixing scope and CDI params", outputKey = "answer")
        String answer(String text);

        @ChatModelSupplier
        static ChatModel chatModel(@V("echo") String fromScope, @CdiBean ModelSelector fromCdi) {
            return fromCdi.select(fromScope);
        }
    }

    public interface MixedParamsSequenceWrapper {

        @SequenceAgent(outputKey = "answer", subAgents = { EchoAgent.class, MixedParamsAgent.class })
        String run(String text);
    }

    public static class FixedResponseChatModel implements ChatModel {

        private final String response;

        public FixedResponseChatModel(String response) {
            this.response = response;
        }

        @Override
        public ChatResponse doChat(ChatRequest request) {
            return ChatResponse.builder().aiMessage(new AiMessage(response)).build();
        }
    }

    public static class EchoResponseChatModel implements ChatModel {

        @Override
        public ChatResponse doChat(ChatRequest request) {
            List<ChatMessage> messages = request.messages();
            for (int i = messages.size() - 1; i >= 0; i--) {
                ChatMessage message = messages.get(i);
                if (message instanceof dev.langchain4j.data.message.UserMessage userMessage) {
                    return ChatResponse.builder().aiMessage(new AiMessage(userMessage.singleText())).build();
                }
            }
            throw new IllegalStateException("No user message found");
        }
    }

    private static int findAvailablePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to find an available HTTP port for dev mode test", e);
        }
    }
}
