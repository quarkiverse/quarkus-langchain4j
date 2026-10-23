package io.quarkiverse.langchain4j.agentic.deployment;

import static io.quarkiverse.langchain4j.agentic.deployment.ScriptedChatModel.clearRequests;
import static io.quarkiverse.langchain4j.agentic.deployment.ScriptedChatModel.conversationsOf;
import static io.quarkiverse.langchain4j.agentic.deployment.ScriptedChatModel.lastConversationOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ActivationCondition;
import dev.langchain4j.agentic.declarative.ChatMemoryProviderSupplier;
import dev.langchain4j.agentic.declarative.ConditionalAgent;
import dev.langchain4j.agentic.declarative.LoopAgent;
import dev.langchain4j.agentic.declarative.ParallelAgent;
import dev.langchain4j.agentic.declarative.ParallelMapperAgent;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.declarative.SupervisorAgent;
import dev.langchain4j.agentic.declarative.SupervisorRequest;
import dev.langchain4j.agentic.declarative.ToolsSupplier;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Chat memory of agents across the agentic patterns, aligned with LangChain4j: agents without a memory are stateless,
 * and agents with a memory keep one conversation per memory id that the other agents do not see.
 * <p>
 * The scenarios after {@code setUp()} also pass on plain LangChain4j. The scenarios before it cover agents declaring
 * {@code @MemoryId} without their own memory, which use the default chat memory store.
 */
public class AgenticPatternsChatMemoryTest extends OpenAiBaseTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(AgenticPatternsChatMemoryTest.class.getDeclaredClasses())
                    .addClass(ScriptedChatModel.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "default-key")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    @ApplicationScoped
    public static class ScriptedChatModelProducer {

        @Produces
        @ApplicationScoped
        ChatModel scriptedModel() {
            return new ScriptedChatModel();
        }
    }

    @Inject
    SequenceStateless sequenceStateless;
    @Inject
    SequenceMemory sequenceMemory;
    @Inject
    SequenceWithTool sequenceWithTool;
    @Inject
    ParallelStateless parallelStateless;
    @Inject
    ParallelMemory parallelMemory;
    @Inject
    LoopStateless loopStateless;
    @Inject
    LoopMemory loopMemory;
    @Inject
    ConditionalStateless conditionalStateless;
    @Inject
    ConditionalMemory conditionalMemory;
    @Inject
    MapperStateless mapperStateless;
    @Inject
    MapperMemory mapperMemory;
    @Inject
    SupervisorStateless supervisorStateless;
    @Inject
    SupervisorMemory supervisorMemory;

    @Inject
    SequenceDefault sequenceDefault;
    @Inject
    ParallelDefault parallelDefault;
    @Inject
    LoopDefault loopDefault;
    @Inject
    ConditionalDefault conditionalDefault;
    @Inject
    MapperDefault mapperDefault;
    @Inject
    SupervisorDefault supervisorDefault;

    @Test
    void parallelMapperRejectsSubAgentWithChatMemory() {
        assertThatThrownBy(() -> mapperMemory.run("mapper-1", List.of("first")))
                .hasStackTraceContaining("Agents with chat memory can't be a subagent of");
    }

    @Test
    void parallelMapperRejectsSubAgentWithDefaultChatMemory() {
        assertThatThrownBy(() -> mapperDefault.run("mapper-default-1", List.of("first")))
                .hasStackTraceContaining("Agents with chat memory can't be a subagent of");
    }

    @Test
    void sequenceWithDefaultChatMemory() {
        sequenceDefault.run("sequence-default-1", "first");
        sequenceDefault.run("sequence-default-1", "second");

        assertThat(lastConversationOf("DEFAULT-A")).containsExactly(
                "USER: DEFAULT-A agent, topic first",
                "AI: ok",
                "USER: DEFAULT-A agent, topic second");
        assertThat(lastConversationOf("DEFAULT-B")).containsExactly(
                "USER: DEFAULT-B agent, topic first",
                "AI: ok",
                "USER: DEFAULT-B agent, topic second");

        sequenceDefault.run("sequence-default-2", "third");

        assertThat(lastConversationOf("DEFAULT-A")).containsExactly("USER: DEFAULT-A agent, topic third");
        assertThat(lastConversationOf("DEFAULT-B")).containsExactly("USER: DEFAULT-B agent, topic third");
    }

    @Test
    void parallelWithDefaultChatMemory() {
        parallelDefault.run("parallel-default-1", "first");
        parallelDefault.run("parallel-default-1", "second");

        assertThat(lastConversationOf("DEFAULT-A")).containsExactly(
                "USER: DEFAULT-A agent, topic first",
                "AI: ok",
                "USER: DEFAULT-A agent, topic second");
        assertThat(lastConversationOf("DEFAULT-B")).containsExactly(
                "USER: DEFAULT-B agent, topic first",
                "AI: ok",
                "USER: DEFAULT-B agent, topic second");

        parallelDefault.run("parallel-default-2", "third");

        assertThat(lastConversationOf("DEFAULT-A")).containsExactly("USER: DEFAULT-A agent, topic third");
        assertThat(lastConversationOf("DEFAULT-B")).containsExactly("USER: DEFAULT-B agent, topic third");
    }

    @Test
    void loopWithDefaultChatMemory() {
        loopDefault.run("loop-default-1", "first");
        loopDefault.run("loop-default-1", "second");

        assertThat(lastConversationOf("DEFAULT-A")).containsExactly(
                "USER: DEFAULT-A agent, topic first",
                "AI: ok",
                "USER: DEFAULT-A agent, topic first",
                "AI: ok",
                "USER: DEFAULT-A agent, topic second",
                "AI: ok",
                "USER: DEFAULT-A agent, topic second");
        assertThat(lastConversationOf("DEFAULT-B")).containsExactly(
                "USER: DEFAULT-B agent, topic first",
                "AI: ok",
                "USER: DEFAULT-B agent, topic first",
                "AI: ok",
                "USER: DEFAULT-B agent, topic second",
                "AI: ok",
                "USER: DEFAULT-B agent, topic second");

        clearRequests();
        loopDefault.run("loop-default-2", "third");

        assertThat(conversationsOf("DEFAULT-A")).containsExactly(
                List.of("USER: DEFAULT-A agent, topic third"),
                List.of("USER: DEFAULT-A agent, topic third", "AI: ok", "USER: DEFAULT-A agent, topic third"));
    }

    @Test
    void conditionalWithDefaultChatMemory() {
        conditionalDefault.run("conditional-default-1", "first");
        conditionalDefault.run("conditional-default-1", "second");

        assertThat(lastConversationOf("DEFAULT-A")).containsExactly(
                "USER: DEFAULT-A agent, topic first",
                "AI: ok",
                "USER: DEFAULT-A agent, topic second");
        assertThat(lastConversationOf("DEFAULT-B")).containsExactly(
                "USER: DEFAULT-B agent, topic first",
                "AI: ok",
                "USER: DEFAULT-B agent, topic second");

        conditionalDefault.run("conditional-default-2", "third");

        assertThat(lastConversationOf("DEFAULT-A")).containsExactly("USER: DEFAULT-A agent, topic third");
        assertThat(lastConversationOf("DEFAULT-B")).containsExactly("USER: DEFAULT-B agent, topic third");
    }

    @Test
    void supervisorWithDefaultChatMemory() {
        supervisorDefault.run("supervisor-default-1", "first");
        supervisorDefault.run("supervisor-default-1", "second");

        assertThat(lastConversationOf("DEFAULT-A")).containsExactly(
                "USER: DEFAULT-A agent, topic first",
                "AI: ok",
                "USER: DEFAULT-A agent, topic second");
        assertThat(lastConversationOf("DEFAULT-B")).containsExactly(
                "USER: DEFAULT-B agent, topic first",
                "AI: ok",
                "USER: DEFAULT-B agent, topic second");

        supervisorDefault.run("supervisor-default-2", "third");

        assertThat(lastConversationOf("DEFAULT-A")).containsExactly("USER: DEFAULT-A agent, topic third");
        assertThat(lastConversationOf("DEFAULT-B")).containsExactly("USER: DEFAULT-B agent, topic third");
    }

    public interface DefaultA {

        @UserMessage("DEFAULT-A agent, topic {{topic}}")
        @Agent(name = "defaultA", description = "Agent A with the default chat memory", outputKey = "a")
        String ask(@MemoryId String memoryId, @V("topic") String topic);
    }

    public interface DefaultB {

        @UserMessage("DEFAULT-B agent, topic {{topic}}")
        @Agent(name = "defaultB", description = "Agent B with the default chat memory", outputKey = "b")
        String ask(@MemoryId String memoryId, @V("topic") String topic);
    }

    public interface SequenceDefault {

        @SequenceAgent(outputKey = "b", subAgents = { DefaultA.class, DefaultB.class })
        String run(@MemoryId String memoryId, @V("topic") String topic);
    }

    public interface ParallelDefault {

        @ParallelAgent(outputKey = "b", subAgents = { DefaultA.class, DefaultB.class })
        String run(@MemoryId String memoryId, @V("topic") String topic);
    }

    public interface LoopDefault {

        @LoopAgent(outputKey = "b", maxIterations = 2, subAgents = { DefaultA.class, DefaultB.class })
        String run(@MemoryId String memoryId, @V("topic") String topic);
    }

    public interface ConditionalDefault {

        @ConditionalAgent(outputKey = "b", subAgents = { DefaultA.class, DefaultB.class })
        String run(@MemoryId String memoryId, @V("topic") String topic);

        @ActivationCondition({ DefaultA.class, DefaultB.class })
        static boolean always(@V("topic") String topic) {
            return true;
        }
    }

    public interface MapperDefault {

        @ParallelMapperAgent(outputKey = "mapped", subAgent = DefaultA.class)
        List<String> run(@MemoryId String memoryId, @V("topics") List<String> topics);
    }

    public interface SupervisorDefault {

        @SupervisorAgent(outputKey = "b", subAgents = { DefaultA.class, DefaultB.class })
        String run(@MemoryId String memoryId, @V("topic") String topic);

        @SupervisorRequest
        static String request(@V("topic") String topic) {
            return topic;
        }
    }

    @BeforeEach
    void setUp() {
        clearRequests();
    }

    @Test
    void sequenceWithoutMemoryId() {
        sequenceStateless.run("first");
        clearRequests();
        sequenceStateless.run("second");

        assertThat(conversationsOf("STATELESS-A")).containsExactly(
                List.of("USER: STATELESS-A agent, topic second"));
        assertThat(conversationsOf("STATELESS-B")).containsExactly(
                List.of("USER: STATELESS-B agent, topic second"));
    }

    @Test
    void sequenceWithChatMemoryProviderSupplier() {
        sequenceMemory.run("sequence-1", "first");
        sequenceMemory.run("sequence-1", "second");

        assertThat(lastConversationOf("MEMORY-A")).containsExactly(
                "USER: MEMORY-A agent, topic first",
                "AI: ok",
                "USER: MEMORY-A agent, topic second");
        assertThat(lastConversationOf("MEMORY-B")).containsExactly(
                "USER: MEMORY-B agent, topic first",
                "AI: ok",
                "USER: MEMORY-B agent, topic second");

        sequenceMemory.run("sequence-2", "third");

        assertThat(lastConversationOf("MEMORY-A")).containsExactly("USER: MEMORY-A agent, topic third");
        assertThat(lastConversationOf("MEMORY-B")).containsExactly("USER: MEMORY-B agent, topic third");
    }

    @Test
    void parallelWithoutMemoryId() {
        parallelStateless.run("first");
        clearRequests();
        parallelStateless.run("second");

        assertThat(conversationsOf("STATELESS-A")).containsExactly(
                List.of("USER: STATELESS-A agent, topic second"));
        assertThat(conversationsOf("STATELESS-B")).containsExactly(
                List.of("USER: STATELESS-B agent, topic second"));
    }

    @Test
    void parallelWithChatMemoryProviderSupplier() {
        parallelMemory.run("parallel-1", "first");
        parallelMemory.run("parallel-1", "second");

        assertThat(lastConversationOf("MEMORY-A")).containsExactly(
                "USER: MEMORY-A agent, topic first",
                "AI: ok",
                "USER: MEMORY-A agent, topic second");
        assertThat(lastConversationOf("MEMORY-B")).containsExactly(
                "USER: MEMORY-B agent, topic first",
                "AI: ok",
                "USER: MEMORY-B agent, topic second");

        parallelMemory.run("parallel-2", "third");

        assertThat(lastConversationOf("MEMORY-A")).containsExactly("USER: MEMORY-A agent, topic third");
        assertThat(lastConversationOf("MEMORY-B")).containsExactly("USER: MEMORY-B agent, topic third");
    }

    @Test
    void loopWithoutMemoryId() {
        loopStateless.run("first");
        clearRequests();
        loopStateless.run("second");

        assertThat(conversationsOf("STATELESS-A")).containsExactly(
                List.of("USER: STATELESS-A agent, topic second"),
                List.of("USER: STATELESS-A agent, topic second"));
        assertThat(conversationsOf("STATELESS-B")).containsExactly(
                List.of("USER: STATELESS-B agent, topic second"),
                List.of("USER: STATELESS-B agent, topic second"));
    }

    @Test
    void loopWithChatMemoryProviderSupplier() {
        loopMemory.run("loop-1", "first");
        loopMemory.run("loop-1", "second");

        assertThat(lastConversationOf("MEMORY-A")).containsExactly(
                "USER: MEMORY-A agent, topic first",
                "AI: ok",
                "USER: MEMORY-A agent, topic first",
                "AI: ok",
                "USER: MEMORY-A agent, topic second",
                "AI: ok",
                "USER: MEMORY-A agent, topic second");
        assertThat(lastConversationOf("MEMORY-B")).containsExactly(
                "USER: MEMORY-B agent, topic first",
                "AI: ok",
                "USER: MEMORY-B agent, topic first",
                "AI: ok",
                "USER: MEMORY-B agent, topic second",
                "AI: ok",
                "USER: MEMORY-B agent, topic second");

        clearRequests();
        loopMemory.run("loop-2", "third");

        assertThat(conversationsOf("MEMORY-A")).containsExactly(
                List.of("USER: MEMORY-A agent, topic third"),
                List.of("USER: MEMORY-A agent, topic third", "AI: ok", "USER: MEMORY-A agent, topic third"));
    }

    @Test
    void conditionalWithoutMemoryId() {
        conditionalStateless.run("first");
        clearRequests();
        conditionalStateless.run("second");

        assertThat(conversationsOf("STATELESS-A")).containsExactly(
                List.of("USER: STATELESS-A agent, topic second"));
        assertThat(conversationsOf("STATELESS-B")).containsExactly(
                List.of("USER: STATELESS-B agent, topic second"));
    }

    @Test
    void conditionalWithChatMemoryProviderSupplier() {
        conditionalMemory.run("conditional-1", "first");
        conditionalMemory.run("conditional-1", "second");

        assertThat(lastConversationOf("MEMORY-A")).containsExactly(
                "USER: MEMORY-A agent, topic first",
                "AI: ok",
                "USER: MEMORY-A agent, topic second");
        assertThat(lastConversationOf("MEMORY-B")).containsExactly(
                "USER: MEMORY-B agent, topic first",
                "AI: ok",
                "USER: MEMORY-B agent, topic second");

        conditionalMemory.run("conditional-2", "third");

        assertThat(lastConversationOf("MEMORY-A")).containsExactly("USER: MEMORY-A agent, topic third");
        assertThat(lastConversationOf("MEMORY-B")).containsExactly("USER: MEMORY-B agent, topic third");
    }

    @Test
    void parallelMapperWithoutMemoryId() {
        mapperStateless.run(List.of("first", "second"));
        mapperStateless.run(List.of("third"));

        assertThat(conversationsOf("STATELESS-A")).containsExactlyInAnyOrder(
                List.of("USER: STATELESS-A agent, topic first"),
                List.of("USER: STATELESS-A agent, topic second"),
                List.of("USER: STATELESS-A agent, topic third"));
    }

    @Test
    void supervisorWithoutMemoryId() {
        supervisorStateless.run("first");
        clearRequests();
        supervisorStateless.run("second");

        assertThat(conversationsOf("STATELESS-A")).containsExactly(
                List.of("USER: STATELESS-A agent, topic second"));
        assertThat(conversationsOf("STATELESS-B")).containsExactly(
                List.of("USER: STATELESS-B agent, topic second"));
    }

    @Test
    void supervisorWithChatMemoryProviderSupplier() {
        supervisorMemory.run("supervisor-1", "first");
        supervisorMemory.run("supervisor-1", "second");

        assertThat(lastConversationOf("MEMORY-A")).containsExactly(
                "USER: MEMORY-A agent, topic first",
                "AI: ok",
                "USER: MEMORY-A agent, topic second");
        assertThat(lastConversationOf("MEMORY-B")).containsExactly(
                "USER: MEMORY-B agent, topic first",
                "AI: ok",
                "USER: MEMORY-B agent, topic second");

        supervisorMemory.run("supervisor-2", "third");

        assertThat(lastConversationOf("MEMORY-A")).containsExactly("USER: MEMORY-A agent, topic third");
        assertThat(lastConversationOf("MEMORY-B")).containsExactly("USER: MEMORY-B agent, topic third");
    }

    @Test
    void agentWithToolsWithoutMemoryId() {
        sequenceWithTool.run("first");
        clearRequests();
        sequenceWithTool.run("second");

        assertThat(conversationsOf("TOOL")).containsExactly(
                List.of("USER: TOOL agent, topic second"),
                List.of("USER: TOOL agent, topic second",
                        "AI: calls lookup",
                        "TOOL_EXECUTION_RESULT: details of second"));
    }

    @Test
    void usersWithDifferentMemoryIdsDoNotSeeEachOther() {
        sequenceMemory.run("user-a", "a1");
        sequenceMemory.run("user-b", "b1");
        sequenceMemory.run("user-a", "a2");

        assertThat(lastConversationOf("MEMORY-A")).containsExactly(
                "USER: MEMORY-A agent, topic a1",
                "AI: ok",
                "USER: MEMORY-A agent, topic a2");

        sequenceMemory.run("user-b", "b2");

        assertThat(lastConversationOf("MEMORY-A")).containsExactly(
                "USER: MEMORY-A agent, topic b1",
                "AI: ok",
                "USER: MEMORY-A agent, topic b2");
    }

    public interface StatelessA {

        @UserMessage("STATELESS-A agent, topic {{topic}}")
        @Agent(name = "statelessA", description = "Agent A", outputKey = "a")
        String ask(@V("topic") String topic);
    }

    public interface StatelessB {

        @UserMessage("STATELESS-B agent, topic {{topic}}")
        @Agent(name = "statelessB", description = "Agent B", outputKey = "b")
        String ask(@V("topic") String topic);
    }

    public interface MemoryA {

        @UserMessage("MEMORY-A agent, topic {{topic}}")
        @Agent(name = "memoryA", description = "Agent A with memory", outputKey = "a")
        String ask(@MemoryId String memoryId, @V("topic") String topic);

        @ChatMemoryProviderSupplier
        static ChatMemory chatMemory(Object memoryId) {
            return MessageWindowChatMemory.withMaxMessages(20);
        }
    }

    public interface MemoryB {

        @UserMessage("MEMORY-B agent, topic {{topic}}")
        @Agent(name = "memoryB", description = "Agent B with memory", outputKey = "b")
        String ask(@MemoryId String memoryId, @V("topic") String topic);

        @ChatMemoryProviderSupplier
        static ChatMemory chatMemory(Object memoryId) {
            return MessageWindowChatMemory.withMaxMessages(20);
        }
    }

    public interface ToolAgent {

        @UserMessage("TOOL agent, topic {{topic}}")
        @Agent(name = "toolAgent", description = "Agent with a tool", outputKey = "a")
        String ask(@V("topic") String topic);

        @ToolsSupplier
        static Object tools() {
            return new TopicTool();
        }
    }

    public static class TopicTool {

        @Tool("Looks up the details of a topic")
        public String lookup(String topic) {
            return "details of " + topic;
        }
    }

    public interface SequenceStateless {

        @SequenceAgent(outputKey = "b", subAgents = { StatelessA.class, StatelessB.class })
        String run(@V("topic") String topic);
    }

    public interface SequenceMemory {

        @SequenceAgent(outputKey = "b", subAgents = { MemoryA.class, MemoryB.class })
        String run(@MemoryId String memoryId, @V("topic") String topic);
    }

    public interface SequenceWithTool {

        @SequenceAgent(outputKey = "a", subAgents = { ToolAgent.class })
        String run(@V("topic") String topic);
    }

    public interface ParallelStateless {

        @ParallelAgent(outputKey = "b", subAgents = { StatelessA.class, StatelessB.class })
        String run(@V("topic") String topic);
    }

    public interface ParallelMemory {

        @ParallelAgent(outputKey = "b", subAgents = { MemoryA.class, MemoryB.class })
        String run(@MemoryId String memoryId, @V("topic") String topic);
    }

    public interface LoopStateless {

        @LoopAgent(outputKey = "b", maxIterations = 2, subAgents = { StatelessA.class, StatelessB.class })
        String run(@V("topic") String topic);
    }

    public interface LoopMemory {

        @LoopAgent(outputKey = "b", maxIterations = 2, subAgents = { MemoryA.class, MemoryB.class })
        String run(@MemoryId String memoryId, @V("topic") String topic);
    }

    public interface ConditionalStateless {

        @ConditionalAgent(outputKey = "b", subAgents = { StatelessA.class, StatelessB.class })
        String run(@V("topic") String topic);

        @ActivationCondition({ StatelessA.class, StatelessB.class })
        static boolean always(@V("topic") String topic) {
            return true;
        }
    }

    public interface ConditionalMemory {

        @ConditionalAgent(outputKey = "b", subAgents = { MemoryA.class, MemoryB.class })
        String run(@MemoryId String memoryId, @V("topic") String topic);

        @ActivationCondition({ MemoryA.class, MemoryB.class })
        static boolean always(@V("topic") String topic) {
            return true;
        }
    }

    public interface MapperStateless {

        @ParallelMapperAgent(outputKey = "mapped", subAgent = StatelessA.class)
        List<String> run(@V("topics") List<String> topics);
    }

    public interface MapperMemory {

        @ParallelMapperAgent(outputKey = "mapped", subAgent = MemoryA.class)
        List<String> run(@MemoryId String memoryId, @V("topics") List<String> topics);
    }

    public interface SupervisorStateless {

        @SupervisorAgent(outputKey = "b", subAgents = { StatelessA.class, StatelessB.class })
        String run(@V("topic") String topic);

        @SupervisorRequest
        static String request(@V("topic") String topic) {
            return topic;
        }
    }

    public interface SupervisorMemory {

        @SupervisorAgent(outputKey = "b", subAgents = { MemoryA.class, MemoryB.class })
        String run(@MemoryId String memoryId, @V("topic") String topic);

        @SupervisorRequest
        static String request(@V("topic") String topic) {
            return topic;
        }
    }
}
