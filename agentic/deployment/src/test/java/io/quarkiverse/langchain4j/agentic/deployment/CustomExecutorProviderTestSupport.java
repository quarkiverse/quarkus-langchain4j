package io.quarkiverse.langchain4j.agentic.deployment;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;

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
import dev.langchain4j.spi.ExecutorProvider;
import io.quarkus.arc.runtime.BeanContainerListener;
import io.quarkus.runtime.annotations.Recorder;

public class CustomExecutorProviderTestSupport {

    static final List<String> workerThreads = new CopyOnWriteArrayList<>();

    public interface LeftAgent {
        @Agent(outputKey = "left")
        @UserMessage("{{input}}")
        String run(@V("input") String input);

        @ChatModelSupplier
        static ChatModel model() {
            return new ThreadCapturingChatModel();
        }
    }

    public interface RightAgent {
        @Agent(outputKey = "right")
        @UserMessage("{{input}}")
        String run(@V("input") String input);

        @ChatModelSupplier
        static ChatModel model() {
            return new ThreadCapturingChatModel();
        }
    }

    public interface ParallelWorkflow {
        @ParallelAgent(outputKey = "result", subAgents = { LeftAgent.class, RightAgent.class })
        List<String> run(@V("input") String input);

        @Output
        static List<String> output(@V("left") String left, @V("right") String right) {
            return List.of(left, right);
        }
    }

    public static class ThreadCapturingChatModel implements ChatModel {
        @Override
        public ChatResponse doChat(ChatRequest request) {
            workerThreads.add(Thread.currentThread().getName());
            return ChatResponse.builder().aiMessage(AiMessage.from("ok")).build();
        }
    }

    public static class NamedExecutor implements Executor {
        private final String name;

        public NamedExecutor(String name) {
            this.name = name;
        }

        @Override
        public void execute(Runnable command) {
            Thread thread = new Thread(command, name);
            thread.setDaemon(true);
            thread.start();
        }
    }

    public static class ServiceProvider implements ExecutorProvider {
        static int instances;
        static int executorCalls;

        public ServiceProvider() {
            instances++;
        }

        @Override
        public Executor executor() {
            executorCalls++;
            return new NamedExecutor("service-provider-worker");
        }
    }

    public static class ProgrammaticProvider implements ExecutorProvider {
        static int executorCalls;

        @Override
        public Executor executor() {
            executorCalls++;
            return new NamedExecutor("programmatic-provider-worker");
        }
    }

    @Recorder
    public static class ProviderRecorder {
        public BeanContainerListener installProgrammaticProvider() {
            return container -> ExecutorProvider.set(new ProgrammaticProvider());
        }
    }
}
