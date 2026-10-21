package io.quarkiverse.langchain4j.agentic.deployment;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.LoopAgent;
import dev.langchain4j.agentic.declarative.ParallelMapperAgent;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentResponse;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.FieldsInput;
import dev.langchain4j.model.decision.response.ChoiceAnswer;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.decision.Decide;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * Concurrent invocations of the same generated agent in one agentic scope, as made by a parallel mapper or by an
 * asynchronous agent invoked again while still running, report each their own model response to the listeners.
 */
public class ConcurrentAgentResponsesTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            // the models are produced by the test
            .overrideConfigKey("quarkus.langchain4j.openai.decision-model.enabled", "false")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "unused");

    private static final List<String> QUESTIONS = IntStream.rangeClosed(1, 8).mapToObj(i -> "question " + i).toList();

    public interface Answerer {
        @UserMessage("Answer {{question}}")
        @Agent(name = "answerer", description = "Answers a question", outputKey = "answer")
        String answer(@V("question") String question);
    }

    public interface AnswerAll {
        @ParallelMapperAgent(outputKey = "answers", subAgent = Answerer.class)
        List<String> answerAll(@V("questions") List<String> questions);
    }

    public interface AsyncAnswerer {
        @UserMessage("Answer {{question}}")
        @Agent(name = "asyncAnswerer", description = "Answers a question", outputKey = "answer", async = true)
        String answer(@V("question") String question);
    }

    public interface AnswerTwice {
        @LoopAgent(outputKey = "answer", maxIterations = 2, subAgents = AsyncAnswerer.class)
        String answerTwice(@V("question") String question);
    }

    public enum Category {
        LEGAL,
        TECHNICAL
    }

    public interface Router {
        @Decide("Which category does this request belong to?")
        @Agent(name = "router", description = "Classifies a request", outputKey = "category")
        Category classify(@V("request") String request);
    }

    public interface RouteAll {
        @ParallelMapperAgent(outputKey = "categories", subAgent = Router.class)
        List<Category> classifyAll(@V("requests") List<String> requests);
    }

    /**
     * Answers only once the expected calls are all in flight, so that the invocations really overlap, and numbers its
     * answers, so that two overlapping invocations with the same input get different responses.
     */
    public static class OverlappingChatModel implements ChatModel {

        private final AtomicInteger calls = new AtomicInteger();
        private volatile CyclicBarrier allCallsInFlight;

        public void expectConcurrentCalls(int parties) {
            calls.set(0);
            allCallsInFlight = new CyclicBarrier(parties);
        }

        @Override
        public ChatResponse doChat(ChatRequest request) {
            int call = calls.incrementAndGet();
            String question = lastUserMessage(request).substring("Answer ".length());
            await(allCallsInFlight);
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from("Answer #" + call + " to " + question))
                    .tokenUsage(tokenUsageOf(call))
                    .build();
        }
    }

    /**
     * Decides only once all the items are in flight, with a probability depending on the item.
     */
    public static class OverlappingDecisionModel implements DecisionModel {

        private final CyclicBarrier allCallsInFlight = new CyclicBarrier(QUESTIONS.size());

        @Override
        public DecisionResponse doDecide(DecisionRequest request) {
            String question = ((FieldsInput) request.input()).fields().get("request").toString();
            await(allCallsInFlight);
            double probability = probabilityOf(question);
            return DecisionResponse.builder()
                    .answer("classify", ChoiceAnswer.builder()
                            .value("LEGAL")
                            .probabilities(Map.of("LEGAL", probability, "TECHNICAL", 1 - probability))
                            .build())
                    .build();
        }
    }

    @ApplicationScoped
    public static class ResponseCollector implements AgentListener {

        private final List<AgentResponse> responses = new CopyOnWriteArrayList<>();

        void clear() {
            responses.clear();
        }

        /**
         * The responses of the invocations of an agent, waiting for them since an async agent reports on its own thread.
         */
        List<AgentResponse> awaitResponsesOf(String agentName, int count) throws InterruptedException {
            long deadline = System.nanoTime() + SECONDS.toNanos(10);
            List<AgentResponse> agentResponses = responsesOf(agentName);
            while (agentResponses.size() < count && System.nanoTime() < deadline) {
                Thread.sleep(10);
                agentResponses = responsesOf(agentName);
            }
            return agentResponses;
        }

        private List<AgentResponse> responsesOf(String agentName) {
            // a parallel mapper appends the index of the item to the name of the subagent
            return responses.stream()
                    .filter(response -> response.agentName().equals(agentName)
                            || response.agentName().startsWith(agentName + "_"))
                    .toList();
        }

        @Override
        public void afterAgentInvocation(AgentResponse agentResponse) {
            responses.add(agentResponse);
        }
    }

    public static class Models {
        @Produces
        @ApplicationScoped
        ChatModel chatModel() {
            return new OverlappingChatModel();
        }

        @Produces
        @ApplicationScoped
        DecisionModel decisionModel() {
            return new OverlappingDecisionModel();
        }
    }

    @Inject
    AnswerAll answerAll;

    @Inject
    AnswerTwice answerTwice;

    @Inject
    RouteAll routeAll;

    @Inject
    ChatModel chatModel;

    @Inject
    ResponseCollector listener;

    @Test
    void itemsOfAParallelMapperReportEachTheirOwnChatRequestAndResponse() throws InterruptedException {
        ((OverlappingChatModel) ClientProxy.unwrap(chatModel)).expectConcurrentCalls(QUESTIONS.size());
        listener.clear();

        answerAll.answerAll(QUESTIONS);

        List<AgentResponse> responses = listener.awaitResponsesOf("answerer", QUESTIONS.size());
        assertThat(responses).hasSize(QUESTIONS.size());
        for (AgentResponse response : responses) {
            String question = itemOf(response);
            assertThat(lastUserMessage(response.chatRequest())).isEqualTo("Answer " + question);
            String answer = response.chatResponse().aiMessage().text();
            assertThat(answer).endsWith(" to " + question);
            assertThat(response.output()).isEqualTo(answer);
            assertThat(response.chatResponse().tokenUsage()).isEqualTo(tokenUsageOf(answer));
        }
        assertThat(responses).extracting(ConcurrentAgentResponsesTest::itemOf).containsExactlyInAnyOrderElementsOf(QUESTIONS);
    }

    @Test
    void itemsOfAParallelMapperReportEachTheirOwnDecisionResponse() throws InterruptedException {
        listener.clear();

        routeAll.classifyAll(QUESTIONS);

        List<AgentResponse> responses = listener.awaitResponsesOf("router", QUESTIONS.size());
        assertThat(responses).hasSize(QUESTIONS.size());
        for (AgentResponse response : responses) {
            String question = itemOf(response);
            assertThat(response.decisionResponse().choice("classify").probabilityOf("LEGAL"))
                    .isEqualTo(probabilityOf(question));
            assertThat(response.output()).isEqualTo(Category.LEGAL);
            assertThat(response.chatRequest()).isNull();
            assertThat(response.chatResponse()).isNull();
        }
        assertThat(responses).extracting(ConcurrentAgentResponsesTest::itemOf).containsExactlyInAnyOrderElementsOf(QUESTIONS);
    }

    @Test
    void asyncAgentInvokedAgainWhileRunningReportsEachInvocationItsOwnResponse() throws InterruptedException {
        ((OverlappingChatModel) ClientProxy.unwrap(chatModel)).expectConcurrentCalls(2);
        listener.clear();

        answerTwice.answerTwice("question 1");

        List<AgentResponse> responses = listener.awaitResponsesOf("asyncAnswerer", 2);
        assertThat(responses).extracting(response -> response.chatResponse().aiMessage().text())
                .containsExactlyInAnyOrder("Answer #1 to question 1", "Answer #2 to question 1");
        for (AgentResponse response : responses) {
            assertThat(lastUserMessage(response.chatRequest())).isEqualTo("Answer question 1");
            assertThat(response.chatResponse().tokenUsage())
                    .isEqualTo(tokenUsageOf(response.chatResponse().aiMessage().text()));
        }
    }

    /**
     * The item of a parallel mapper invocation, from the index the mapper appends to the id of the subagent: the named
     * inputs of a mapper item do not include the item.
     */
    private static String itemOf(AgentResponse response) {
        String agentId = response.agentId();
        return QUESTIONS.get(Integer.parseInt(agentId.substring(agentId.lastIndexOf('_') + 1)));
    }

    private static String lastUserMessage(ChatRequest request) {
        return dev.langchain4j.data.message.UserMessage.findLast(request.messages()).orElseThrow().singleText();
    }

    private static TokenUsage tokenUsageOf(int call) {
        return new TokenUsage(10 * call, call);
    }

    /**
     * The token usage of the model call that produced an answer numbered by {@link OverlappingChatModel}.
     */
    private static TokenUsage tokenUsageOf(String answer) {
        return tokenUsageOf(Integer.parseInt(answer.substring("Answer #".length(), answer.indexOf(' ', "Answer #".length()))));
    }

    private static double probabilityOf(String question) {
        return 0.5 + Integer.parseInt(question.substring("question ".length())) / 20.0;
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(10, SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the overlapping model calls", e);
        } catch (Exception e) {
            throw new IllegalStateException("The model calls did not overlap", e);
        }
    }
}
