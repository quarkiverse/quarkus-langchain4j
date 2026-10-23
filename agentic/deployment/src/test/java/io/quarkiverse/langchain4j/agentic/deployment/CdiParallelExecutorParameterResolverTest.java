package io.quarkiverse.langchain4j.agentic.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import jakarta.enterprise.context.ContextNotActiveException;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Qualifier;
import jakarta.inject.Singleton;

import org.eclipse.microprofile.context.ManagedExecutor;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.declarative.DeclarativeUtil;
import dev.langchain4j.agentic.declarative.ParallelAgent;
import dev.langchain4j.agentic.declarative.ParallelExecutor;
import dev.langchain4j.agentic.declarative.ParallelMapperAgent;
import dev.langchain4j.agentic.declarative.SupplierParameterResolver;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.agentic.runtime.CdiBean;
import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusUnitTest;

public class CdiParallelExecutorParameterResolverTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(LeftAgent.class, RightAgent.class, MappingAgent.class,
                            ParallelWorkflow.class, ParallelMapperWorkflow.class,
                            ExecutorSupplier.class, InheritedParallelWorkflow.class, ExecutorSelection.class,
                            NamedParallelMapperWorkflow.class, NoArgParallelMapperWorkflow.class,
                            CustomResolverWorkflow.class, ExecutorToken.class,
                            ContextCheckingChatModel.class, RequestState.class, Captures.class,
                            ExecutorProducer.class, ExecutionPool.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "unused");

    @Inject
    ParallelWorkflow parallelWorkflow;

    @Inject
    ParallelMapperWorkflow parallelMapperWorkflow;

    @Inject
    RequestState requestState;

    @Inject
    Captures captures;

    @BeforeEach
    void reset() {
        captures.observations.clear();
    }

    @Test
    @ActivateRequestContext
    void qualifiedExecutorIsInjectedIntoParallelExecutorAndPropagatesRequestContext() {
        requestState.setValue("parallel");
        long callerThreadId = Thread.currentThread().getId();

        parallelWorkflow.run("test");

        assertThat(captures.observations).hasSize(2).allSatisfy(observation -> {
            assertThat(observation.requestValue()).isEqualTo("parallel");
            assertThat(observation.threadId()).isNotEqualTo(callerThreadId);
        });
    }

    @Test
    @ActivateRequestContext
    void managedExecutorCdiBeanIsInjectedIntoParallelMapperExecutorAndPropagatesRequestContext() {
        requestState.setValue("mapper");
        long callerThreadId = Thread.currentThread().getId();

        parallelMapperWorkflow.run(List.of("one", "two"));

        assertThat(captures.observations).hasSize(2).allSatisfy(observation -> {
            assertThat(observation.requestValue()).isEqualTo("mapper");
            assertThat(observation.threadId()).isNotEqualTo(callerThreadId);
        });
    }

    @Test
    @ActivateRequestContext
    void inheritedSupplierResolvesBeanUsedOnlyBySupplier() {
        requestState.setValue("inherited");
        long callerThreadId = Thread.currentThread().getId();

        Arc.container().instance(InheritedParallelWorkflow.class).get().run("test");

        assertThat(captures.observations).hasSize(2).allSatisfy(observation -> {
            assertThat(observation.requestValue()).isEqualTo("inherited");
            assertThat(observation.threadId()).isNotEqualTo(callerThreadId);
        });
    }

    @Test
    @ActivateRequestContext
    void namedQualifierWithoutCdiBeanIsResolved() {
        requestState.setValue("named");
        long callerThreadId = Thread.currentThread().getId();

        Arc.container().instance(NamedParallelMapperWorkflow.class).get().run(List.of("one", "two"));

        assertThat(captures.observations).hasSize(2).allSatisfy(observation -> {
            assertThat(observation.requestValue()).isEqualTo("named");
            assertThat(observation.threadId()).isNotEqualTo(callerThreadId);
        });
    }

    @Test
    @ActivateRequestContext
    void noArgSupplierStillUsesProvidedExecutor() {
        requestState.setValue("no-args");
        long callerThreadId = Thread.currentThread().getId();

        assertThat(Arc.container().instance(NoArgParallelMapperWorkflow.class).get().run(List.of("one", "two")))
                .containsExactly("ok", "ok");

        assertThat(captures.observations).hasSize(2).allSatisfy(observation -> {
            assertThat(observation.requestValue()).isEqualTo("no-args");
            assertThat(observation.threadId()).isEqualTo(callerThreadId);
        });
    }

    @Test
    @ActivateRequestContext
    void customSupplierParameterResolverIsNotRestrictedToCdiAnnotations() {
        requestState.setValue("custom-resolver");
        long callerThreadId = Thread.currentThread().getId();
        SupplierParameterResolver resolver = new SupplierParameterResolver() {
            @Override
            public boolean supports(Context context) {
                return context.parameter().getType().equals(ExecutorToken.class);
            }

            @Override
            public Object resolve(Context context) {
                return new ExecutorToken(Arc.container().instance(ManagedExecutor.class).get());
            }
        };
        DeclarativeUtil.addSupplierParameterResolver(resolver);
        try {
            Arc.container().instance(CustomResolverWorkflow.class).get().run(List.of("one", "two"));

            assertThat(captures.observations).hasSize(2).allSatisfy(observation -> {
                assertThat(observation.requestValue()).isEqualTo("custom-resolver");
                assertThat(observation.threadId()).isNotEqualTo(callerThreadId);
            });
        } finally {
            DeclarativeUtil.getSupplierParameterResolvers().remove(resolver);
        }
    }

    @Test
    @ActivateRequestContext
    void rawExecutorCannotAccessCallerRequestContext() {
        requestState.setValue("raw-executor");
        assertThat(requestState.getValue()).isEqualTo("raw-executor");
        ExecutorService rawExecutor = Executors.newSingleThreadExecutor();
        try {
            CompletableFuture<String> requestValue = CompletableFuture.supplyAsync(
                    () -> Arc.container().instance(RequestState.class).get().getValue(), rawExecutor);

            assertThatThrownBy(() -> requestValue.get(5, TimeUnit.SECONDS))
                    .hasRootCauseInstanceOf(ContextNotActiveException.class);
        } finally {
            rawExecutor.shutdownNow();
        }
    }

    public interface LeftAgent {

        @Agent(description = "Left test agent", outputKey = "left")
        @UserMessage("{{input}}")
        String run(@V("input") String input);

        @ChatModelSupplier
        static ChatModel model() {
            return new ContextCheckingChatModel();
        }
    }

    public interface RightAgent {

        @Agent(description = "Right test agent", outputKey = "right")
        @UserMessage("{{input}}")
        String run(@V("input") String input);

        @ChatModelSupplier
        static ChatModel model() {
            return new ContextCheckingChatModel();
        }
    }

    public interface MappingAgent {

        @Agent(description = "Mapping test agent", outputKey = "mapped")
        @UserMessage("{{item}}")
        String run(@V("item") String item);

        @ChatModelSupplier
        static ChatModel model() {
            return new ContextCheckingChatModel();
        }
    }

    public interface ParallelWorkflow {

        @ParallelAgent(outputKey = "result", subAgents = { LeftAgent.class, RightAgent.class })
        List<String> run(@V("input") String input);

        @ParallelExecutor
        static Executor executor(@ExecutionPool("parallel") Executor executor) {
            return executor;
        }
    }

    public interface ExecutorSupplier {

        @ParallelExecutor
        static Executor executor(@CdiBean @ExecutionPool("inherited") ExecutorSelection selection) {
            return selection.executor();
        }
    }

    public interface InheritedParallelWorkflow extends ExecutorSupplier {

        @ParallelAgent(outputKey = "result", subAgents = { LeftAgent.class, RightAgent.class })
        List<String> run(@V("input") String input);
    }

    public interface NamedParallelMapperWorkflow {

        @ParallelMapperAgent(subAgent = MappingAgent.class)
        List<String> run(@V("items") List<String> items);

        @ParallelExecutor
        static Executor executor(@Named("named") Executor executor) {
            return executor;
        }
    }

    public interface NoArgParallelMapperWorkflow {

        @ParallelMapperAgent(subAgent = MappingAgent.class)
        List<String> run(@V("items") List<String> items);

        @ParallelExecutor
        static Executor executor() {
            return Runnable::run;
        }
    }

    public interface CustomResolverWorkflow {

        @ParallelMapperAgent(subAgent = MappingAgent.class)
        List<String> run(@V("items") List<String> items);

        @ParallelExecutor
        static Executor executor(ExecutorToken token) {
            return token.executor();
        }
    }

    public record ExecutorToken(Executor executor) {
    }

    public interface ParallelMapperWorkflow {

        @ParallelMapperAgent(subAgent = MappingAgent.class)
        List<String> run(@V("items") List<String> items);

        @ParallelExecutor
        static Executor executor(@CdiBean ManagedExecutor executor) {
            return executor;
        }
    }

    public static class ContextCheckingChatModel implements ChatModel {

        @Override
        public ChatResponse doChat(ChatRequest request) {
            RequestState requestState = Arc.container().instance(RequestState.class).get();
            Arc.container().instance(Captures.class).get().observations
                    .add(new Observation(requestState.getValue(), Thread.currentThread().getId()));
            return ChatResponse.builder().aiMessage(AiMessage.from("ok")).build();
        }
    }

    record Observation(String requestValue, long threadId) {
    }

    @RequestScoped
    public static class RequestState {
        private String value;

        public void setValue(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    @Singleton
    public static class Captures {
        final List<Observation> observations = new CopyOnWriteArrayList<>();
    }

    @Singleton
    public static class ExecutorProducer {

        @Produces
        @ExecutionPool("parallel")
        Executor parallelExecutor(ManagedExecutor executor) {
            return executor;
        }

        @Produces
        @ExecutionPool("unused")
        Executor unusedExecutor() {
            return Runnable::run;
        }

        @Produces
        @Named("named")
        Executor namedExecutor(ManagedExecutor executor) {
            return executor;
        }
    }

    @Singleton
    @ExecutionPool("inherited")
    public static class ExecutorSelection {

        @Inject
        ManagedExecutor executor;

        public Executor executor() {
            return executor;
        }
    }

    @Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE })
    public @interface ExecutionPool {
        String value();
    }
}
