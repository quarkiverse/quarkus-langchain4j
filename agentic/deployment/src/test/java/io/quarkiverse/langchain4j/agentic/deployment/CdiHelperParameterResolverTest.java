package io.quarkiverse.langchain4j.agentic.deployment;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.ContextNotActiveException;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.inject.Qualifier;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.agent.ErrorContext;
import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
import dev.langchain4j.agentic.agent.MissingArgumentException;
import dev.langchain4j.agentic.declarative.A2AClientAgent;
import dev.langchain4j.agentic.declarative.A2AServerUrlSupplier;
import dev.langchain4j.agentic.declarative.ActivationCondition;
import dev.langchain4j.agentic.declarative.AgentListenerSupplier;
import dev.langchain4j.agentic.declarative.BeforeCall;
import dev.langchain4j.agentic.declarative.ChatMemoryProviderSupplier;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.declarative.ConditionalAgent;
import dev.langchain4j.agentic.declarative.ErrorHandler;
import dev.langchain4j.agentic.declarative.ExitCondition;
import dev.langchain4j.agentic.declarative.HumanInTheLoop;
import dev.langchain4j.agentic.declarative.LoopAgent;
import dev.langchain4j.agentic.declarative.Output;
import dev.langchain4j.agentic.declarative.ParallelAgent;
import dev.langchain4j.agentic.declarative.ParallelExecutor;
import dev.langchain4j.agentic.declarative.PlannerAgent;
import dev.langchain4j.agentic.declarative.PlannerSupplier;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.declarative.SupervisorAgent;
import dev.langchain4j.agentic.declarative.SupervisorRequest;
import dev.langchain4j.agentic.declarative.SystemMessageProviderSupplier;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentRequest;
import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.supervisor.SupervisorResponseStrategy;
import dev.langchain4j.agentic.workflow.impl.SequentialPlanner;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.agentic.runtime.CdiBean;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ClientProxy;
import io.quarkus.arc.ManagedContext;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * Verifies that the parameters annotated with {@code @CdiBean}, or with a CDI qualifier, are resolved from CDI on every
 * kind of static method of the agents, mixed with the parameters read from the {@code AgenticScope}.
 */
public class CdiHelperParameterResolverTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "whatever")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    // ── Beans ──

    @Qualifier
    @Retention(RUNTIME)
    @Target({ FIELD, METHOD, PARAMETER, TYPE })
    public @interface Loud {
    }

    @ApplicationScoped
    public static class Threshold {
        public int value() {
            return 3;
        }
    }

    @ApplicationScoped
    public static class Policy {
        public boolean allows(String name) {
            return !"Voldemort".equals(name);
        }
    }

    @ApplicationScoped
    public static class Formatter {
        public String format(String text) {
            return text.toUpperCase();
        }
    }

    @Loud
    @ApplicationScoped
    public static class LoudFormatter extends Formatter {
        @Override
        public String format(String text) {
            return super.format(text) + "!";
        }
    }

    @ApplicationScoped
    public static class Defaults {
        public String topic() {
            return "dragons";
        }
    }

    @ApplicationScoped
    public static class Console {
        public String ask(String question) {
            return question + "? yes";
        }
    }

    @ApplicationScoped
    public static class PlannerFactory {
        private final AtomicInteger created = new AtomicInteger();

        public Planner create() {
            created.incrementAndGet();
            return new SequentialPlanner();
        }

        public int created() {
            return created.get();
        }
    }

    @ApplicationScoped
    public static class CountingExecutor {
        private final AtomicInteger executions = new AtomicInteger();

        public Executor executor() {
            return command -> {
                executions.incrementAndGet();
                command.run();
            };
        }

        public int executions() {
            return executions.get();
        }
    }

    /** Creates listeners: a bean implementing AgentListener would be registered on every agent. */
    @ApplicationScoped
    public static class ListenerFactory {
        private final List<String> invokedAgents = new CopyOnWriteArrayList<>();

        public AgentListener listener() {
            return new AgentListener() {
                @Override
                public void beforeAgentInvocation(AgentRequest agentRequest) {
                    invokedAgents.add(agentRequest.agentName());
                }
            };
        }

        public List<String> invokedAgents() {
            return invokedAgents;
        }
    }

    @ApplicationScoped
    public static class MemoryStore {
        private final Map<Object, ChatMemory> memories = new ConcurrentHashMap<>();

        public ChatMemory memoryOf(Object memoryId) {
            return memories.computeIfAbsent(memoryId, id -> MessageWindowChatMemory.builder()
                    .id(id)
                    .maxMessages(10)
                    .build());
        }

        public Set<Object> memoryIds() {
            return memories.keySet();
        }
    }

    @ApplicationScoped
    public static class Persona {
        public String role() {
            return "Butler";
        }
    }

    @ApplicationScoped
    public static class Endpoints {
        private final AtomicInteger calls = new AtomicInteger();

        public String a2aUrl() {
            calls.incrementAndGet();
            return "http://localhost:1";
        }

        public int calls() {
            return calls.get();
        }
    }

    @RequestScoped
    public static class CurrentUser {
        private String name = "anonymous";

        public String name() {
            return name;
        }

        public void name(String name) {
            this.name = name;
        }
    }

    /** Replies with the system message and the last user message it receives. */
    public static class SystemEchoModel implements ChatModel {
        @Override
        public ChatResponse doChat(ChatRequest request) {
            String system = request.messages().stream()
                    .filter(SystemMessage.class::isInstance)
                    .map(message -> ((SystemMessage) message).text())
                    .findFirst()
                    .orElse("");
            ChatMessage last = request.messages().get(request.messages().size() - 1);
            String user = last instanceof dev.langchain4j.data.message.UserMessage userMessage
                    ? userMessage.singleText()
                    : "";
            return ChatResponse.builder().aiMessage(new AiMessage(system + " | " + user)).build();
        }
    }

    // ── @ExitCondition ──

    public static class Counter {
        @Agent(outputKey = "count")
        public static int increment(@V("count") int count) {
            return count + 1;
        }
    }

    public interface CountingLoop {
        @LoopAgent(outputKey = "count", maxIterations = 10, subAgents = Counter.class)
        int count(@V("count") int count);

        @ExitCondition
        static boolean reached(@V("count") int count, @CdiBean Threshold threshold) {
            return count >= threshold.value();
        }
    }

    @Inject
    CountingLoop countingLoop;

    @Test
    void exitCondition() {
        assertThat(countingLoop.count(0)).isEqualTo(3);
    }

    // ── Non-AI agent, and @Output with a qualifier only ──

    public static class Greeter {
        @Agent(outputKey = "greeting")
        public static String greet(@V("name") String name, @CdiBean Formatter formatter) {
            return formatter.format("Hello " + name);
        }
    }

    public interface GreetingSequence {
        @SequenceAgent(outputKey = "result", subAgents = Greeter.class)
        String greet(@V("name") String name);

        @Output
        static String output(AgenticScope agenticScope, @Loud Formatter formatter) {
            return formatter.format(agenticScope.readState("greeting", "no greeting"));
        }
    }

    @Inject
    GreetingSequence greetingSequence;

    @Test
    void nonAiAgentAndOutput() {
        assertThat(greetingSequence.greet("Mario")).isEqualTo("HELLO MARIO!");

        AgentInstance greeter = ((AgentInstance) ClientProxy.unwrap(greetingSequence)).subagents().get(0);
        assertThat(greeter.arguments()).extracting(AgentArgument::name).containsExactly("name");
    }

    // ── @ActivationCondition with value ──

    public static class Shouter {
        @Agent(outputKey = "shout")
        public static String shout(@V("name") String name) {
            return name.toUpperCase() + "!";
        }
    }

    public interface ConditionalShout {
        @ConditionalAgent(outputKey = "shout", subAgents = Shouter.class)
        String shout(@V("name") String name);

        @ActivationCondition(Shouter.class)
        static boolean allowed(@V("name") String name, @CdiBean Policy policy) {
            return policy.allows(name);
        }
    }

    @Inject
    ConditionalShout conditionalShout;

    @Test
    void conditionalActivationCondition() {
        assertThat(conditionalShout.shout("Mario")).isEqualTo("MARIO!");
        assertThat(conditionalShout.shout("Voldemort")).isNull();
    }

    // ── @BeforeCall and @ErrorHandler ──

    public static class TopicWriter {
        @Agent(outputKey = "story")
        public static String write(@V("topic") String topic) {
            return "A story about " + topic;
        }
    }

    public interface StoryWithDefaults {
        @SequenceAgent(outputKey = "story", subAgents = TopicWriter.class)
        String write(@V("topic") String topic);

        @BeforeCall
        static void beforeCall(AgenticScope agenticScope, @CdiBean Defaults defaults) {
            agenticScope.writeStateIfAbsent("topic", defaults.topic());
        }
    }

    @Inject
    StoryWithDefaults storyWithDefaults;

    @Test
    void beforeCall() {
        assertThat(storyWithDefaults.write(null)).isEqualTo("A story about dragons");
        assertThat(storyWithDefaults.write("wizards")).isEqualTo("A story about wizards");
    }

    public interface StoryWithRecovery {
        @SequenceAgent(outputKey = "story", subAgents = TopicWriter.class)
        String write(@V("topic") String topic);

        @ErrorHandler
        static ErrorRecoveryResult recover(@CdiBean Defaults defaults, ErrorContext errorContext) {
            if (errorContext.exception() instanceof MissingArgumentException missing
                    && missing.argumentName().equals("topic")) {
                errorContext.agenticScope().writeState("topic", defaults.topic());
                return ErrorRecoveryResult.retry();
            }
            return ErrorRecoveryResult.throwException();
        }
    }

    @Inject
    StoryWithRecovery storyWithRecovery;

    @Test
    void errorHandler() {
        assertThat(storyWithRecovery.write(null)).isEqualTo("A story about dragons");
    }

    // ── @AgentListenerSupplier on a workflow agent ──

    public interface ListenedStory {
        @SequenceAgent(outputKey = "story", subAgents = TopicWriter.class)
        String write(@V("topic") String topic);

        @AgentListenerSupplier
        static AgentListener listener(@CdiBean ListenerFactory factory) {
            return factory.listener();
        }
    }

    @Inject
    ListenedStory listenedStory;

    @Inject
    ListenerFactory listenerFactory;

    @Test
    void workflowListenerSupplier() {
        assertThat(listenedStory.write("wizards")).isEqualTo("A story about wizards");
        assertThat(listenerFactory.invokedAgents()).contains("write");
    }

    // ── @PlannerSupplier ──

    public interface PlannedStory {
        @PlannerAgent(outputKey = "story", subAgents = TopicWriter.class)
        String write(@V("topic") String topic);

        @PlannerSupplier
        static Planner planner(@CdiBean PlannerFactory factory) {
            return factory.create();
        }
    }

    @Inject
    PlannedStory plannedStory;

    @Inject
    PlannerFactory plannerFactory;

    @Test
    void plannerSupplier() {
        assertThat(plannedStory.write("dragons")).isEqualTo("A story about dragons");
        int created = plannerFactory.created();
        assertThat(plannedStory.write("wizards")).isEqualTo("A story about wizards");
        assertThat(plannerFactory.created()).isEqualTo(created + 1);
    }

    // ── @ParallelExecutor ──

    public static class FoodExpert {
        @Agent(outputKey = "meal")
        public static String meal(@V("mood") String mood) {
            return mood + " pasta";
        }
    }

    public static class MovieExpert {
        @Agent(outputKey = "movie")
        public static String movie(@V("mood") String mood) {
            return mood + " comedy";
        }
    }

    public interface EveningPlanner {
        @ParallelAgent(outputKey = "plan", subAgents = { FoodExpert.class, MovieExpert.class })
        String plan(@V("mood") String mood);

        @ParallelExecutor
        static Executor executor(@CdiBean CountingExecutor executor) {
            return executor.executor();
        }

        @Output
        static String combine(@V("meal") String meal, @V("movie") String movie) {
            return meal + " and " + movie;
        }
    }

    @Inject
    EveningPlanner eveningPlanner;

    @Inject
    CountingExecutor countingExecutor;

    @Test
    void parallelExecutor() {
        int executions = countingExecutor.executions();
        assertThat(eveningPlanner.plan("happy")).isEqualTo("happy pasta and happy comedy");
        assertThat(countingExecutor.executions()).isEqualTo(executions + 2);
    }

    // ── @HumanInTheLoop ──

    public interface Confirmation {
        @HumanInTheLoop(description = "Asks a confirmation", outputKey = "confirmation")
        static String ask(@V("name") String name, @CdiBean Console console) {
            return console.ask("Confirm " + name);
        }
    }

    public interface ConfirmationSequence {
        @SequenceAgent(outputKey = "confirmation", subAgents = Confirmation.class)
        String confirm(@V("name") String name);
    }

    @Inject
    ConfirmationSequence confirmationSequence;

    @Test
    void humanInTheLoop() {
        assertThat(confirmationSequence.confirm("Mario")).isEqualTo("Confirm Mario? yes");

        AgentInstance humanInTheLoop = ((AgentInstance) ClientProxy.unwrap(confirmationSequence)).subagents().get(0);
        assertThat(humanInTheLoop.arguments()).extracting(AgentArgument::name).containsExactly("name");
    }

    // ── AI agent: memory id based suppliers ──

    public interface Assistant {
        @UserMessage("{{question}}")
        @Agent(outputKey = "answer")
        String answer(@MemoryId String memoryId, @V("question") String question);

        @ChatModelSupplier
        static ChatModel chatModel() {
            return new SystemEchoModel();
        }

        @SystemMessageProviderSupplier
        static String systemMessage(Object memoryId, @CdiBean Persona persona) {
            return persona.role() + " of " + memoryId;
        }

        @ChatMemoryProviderSupplier
        static ChatMemory chatMemory(Object memoryId, @CdiBean MemoryStore store) {
            return store.memoryOf(memoryId);
        }
    }

    public interface AssistantSequence {
        @SequenceAgent(outputKey = "answer", subAgents = Assistant.class)
        String ask(@MemoryId String memoryId, @V("question") String question);
    }

    @Inject
    AssistantSequence assistantSequence;

    @Inject
    MemoryStore memoryStore;

    @Test
    void memoryIdSuppliers() {
        assertThat(assistantSequence.ask("mario", "What time is it?")).isEqualTo("Butler of mario | What time is it?");
        assertThat(memoryStore.memoryIds()).containsExactly("mario");
    }

    // ── @A2AServerUrlSupplier ──

    public interface RemoteWriter {
        @A2AClientAgent(description = "Writes a story remotely", outputKey = "story")
        String write(@V("topic") String topic);

        @A2AServerUrlSupplier
        static String a2aServerUrl(@CdiBean Endpoints endpoints) {
            return endpoints.a2aUrl();
        }
    }

    public interface RemoteStory {
        @SequenceAgent(outputKey = "story", subAgents = RemoteWriter.class)
        String write(@V("topic") String topic);
    }

    @Inject
    RemoteStory remoteStory;

    @Inject
    Endpoints endpoints;

    @Test
    void a2aServerUrlSupplier() {
        // there is no A2A server: the URL supplied by the bean is used to fetch the agent card
        assertThatThrownBy(() -> remoteStory.write("dragons")).hasStackTraceContaining("Failed to obtain agent card");
        assertThat(endpoints.calls()).isPositive();
    }

    // ── @SupervisorRequest, and the chat model of a supervisor ──

    /** Provides a planner model answering "done" at once, and recording the messages it receives. */
    @ApplicationScoped
    public static class PlannerModels {
        private final List<String> receivedTexts = new CopyOnWriteArrayList<>();

        public ChatModel planner() {
            return new ChatModel() {
                @Override
                public ChatResponse doChat(ChatRequest request) {
                    for (ChatMessage message : request.messages()) {
                        if (message instanceof SystemMessage systemMessage) {
                            receivedTexts.add(systemMessage.text());
                        } else if (message instanceof dev.langchain4j.data.message.UserMessage userMessage) {
                            receivedTexts.add(userMessage.singleText());
                        }
                    }
                    return ChatResponse.builder()
                            .aiMessage(new AiMessage(
                                    "{\"agentName\": \"done\", \"arguments\": {\"response\": \"nothing to do\"}}"))
                            .build();
                }
            };
        }

        public List<String> receivedTexts() {
            return receivedTexts;
        }
    }

    public interface Exchanger {
        @UserMessage("Convert {{euros}} euros to dollars")
        @Agent(description = "Converts an amount of euros to dollars", outputKey = "dollars")
        String exchange(@V("euros") double euros);

        @ChatModelSupplier
        static ChatModel chatModel() {
            return new SystemEchoModel();
        }
    }

    public interface ExchangeSupervisor {
        @SupervisorAgent(outputKey = "result", subAgents = Exchanger.class, responseStrategy = SupervisorResponseStrategy.LAST)
        String invoke(@V("request") String request);

        @ChatModelSupplier
        static ChatModel planner(@CdiBean PlannerModels models) {
            return models.planner();
        }

        @SupervisorRequest
        static String request(@V("request") String request, @CdiBean Persona persona) {
            return "As a " + persona.role() + ", " + request;
        }
    }

    @Inject
    ExchangeSupervisor exchangeSupervisor;

    @Inject
    PlannerModels plannerModels;

    @Test
    void supervisorRequestAndChatModel() {
        // the planner answers "done" at once, so no subagent is invoked
        exchangeSupervisor.invoke("convert 100 EUR to USD");
        assertThat(String.join("\n", plannerModels.receivedTexts())).contains("As a Butler, convert 100 EUR to USD");
    }

    // ── A request scoped bean ──

    public interface PersonalGreeting {
        @SequenceAgent(outputKey = "personalGreeting", subAgents = Shouter.class)
        String greet(@V("name") String name);

        @Output
        static String output(@V("shout") String shout, @CdiBean CurrentUser user) {
            return shout + " from " + user.name();
        }
    }

    @Inject
    PersonalGreeting personalGreeting;

    @Inject
    CurrentUser currentUser;

    @Test
    void requestScopedBeanInsideARequest() throws Exception {
        String greeting = onNewThread(() -> {
            ManagedContext requestContext = Arc.container().requestContext();
            requestContext.activate();
            try {
                currentUser.name("Mario");
                return personalGreeting.greet("Luigi");
            } finally {
                requestContext.terminate();
            }
        });
        assertThat(greeting).isEqualTo("LUIGI! from Mario");
    }

    @Test
    void requestScopedBeanOutsideARequest() {
        assertThatThrownBy(() -> onNewThread(() -> personalGreeting.greet("Luigi")))
                .hasRootCauseInstanceOf(ContextNotActiveException.class);
    }

    private static <T> T onNewThread(java.util.function.Supplier<T> task) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            return CompletableFuture.supplyAsync(task, executor).get();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof Exception cause) {
                throw cause;
            }
            throw e;
        } finally {
            executor.shutdown();
        }
    }
}
