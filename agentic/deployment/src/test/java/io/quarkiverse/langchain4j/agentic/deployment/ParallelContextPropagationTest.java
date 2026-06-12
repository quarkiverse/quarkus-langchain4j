package io.quarkiverse.langchain4j.agentic.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.declarative.Output;
import dev.langchain4j.agentic.declarative.ParallelAgent;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusUnitTest;

public class ParallelContextPropagationTest {

    static final List<Thread> workerThreads = new CopyOnWriteArrayList<>();

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(ContextCheckingChatModel.class, RequestData.class,
                            ContextCheckingAgent.class, EchoAgent.class, ParallelContextAgent.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "unused")
            .overrideConfigKey("quarkus.otel.traces.exporter", "none");

    @Inject
    ParallelContextAgent agent;

    @Inject
    RequestData requestData;

    @Test
    @ActivateRequestContext
    void requestContextPropagatedToParallelWorkerThreads() {
        String marker = UUID.randomUUID().toString();
        requestData.setMarker(marker);
        workerThreads.clear();

        assertThat(agent.run("test")).containsExactly(marker, marker);
        assertThat(workerThreads).hasSize(2)
                .allSatisfy(thread -> assertThat(thread).isNotSameAs(Thread.currentThread()));
    }

    @RequestScoped
    public static class RequestData {
        private String marker;

        public String getMarker() {
            return marker;
        }

        public void setMarker(String marker) {
            this.marker = marker;
        }
    }

    public interface ContextCheckingAgent {
        @ChatModelSupplier
        static ChatModel chatModel() {
            return new ContextCheckingChatModel();
        }

        @UserMessage("Check: {{input}}")
        @Agent(outputKey = "check")
        String check(@V("input") String input);
    }

    public interface EchoAgent {
        @ChatModelSupplier
        static ChatModel chatModel() {
            return new ContextCheckingChatModel();
        }

        @UserMessage("Echo: {{input}}")
        @Agent(outputKey = "echo")
        String echo(@V("input") String input);
    }

    public interface ParallelContextAgent {
        @ParallelAgent(outputKey = "result", subAgents = { ContextCheckingAgent.class, EchoAgent.class })
        List<String> run(@V("input") String input);

        @Output
        static List<String> output(@V("check") String check, @V("echo") String echo) {
            return List.of(check, echo);
        }
    }

    public static class ContextCheckingChatModel implements ChatModel {
        @Override
        public ChatResponse doChat(ChatRequest request) {
            workerThreads.add(Thread.currentThread());
            assertThat(Arc.container().requestContext().isActive())
                    .as("CDI request context should be active on the worker")
                    .isTrue();
            String marker = Arc.container().instance(RequestData.class).get().getMarker();
            return ChatResponse.builder().aiMessage(AiMessage.from(marker)).build();
        }
    }
}
