package org.acme.examples.aiservices;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static dev.langchain4j.test.guardrail.GuardrailAssertions.assertThat;
import static dev.langchain4j.test.guardrail.GuardrailAssertions.assertThatExceptionOfType;
import static dev.langchain4j.test.guardrail.GuardrailAssertions.atIndex;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.stubbing.Scenario;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailException;
import dev.langchain4j.guardrail.InputGuardrailResult;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailException;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.observability.api.event.AiServiceCompletedEvent;
import dev.langchain4j.observability.api.event.AiServiceErrorEvent;
import dev.langchain4j.observability.api.event.AiServiceEvent;
import dev.langchain4j.observability.api.event.AiServiceResponseReceivedEvent;
import dev.langchain4j.observability.api.event.AiServiceStartedEvent;
import dev.langchain4j.observability.api.event.GuardrailExecutedEvent;
import dev.langchain4j.observability.api.event.InputGuardrailExecutedEvent;
import dev.langchain4j.observability.api.event.OutputGuardrailExecutedEvent;
import dev.langchain4j.observability.api.event.ToolExecutedEvent;
import dev.langchain4j.observability.event.DefaultAiServiceCompletedEvent;
import dev.langchain4j.observability.event.DefaultAiServiceErrorEvent;
import dev.langchain4j.observability.event.DefaultAiServiceResponseReceivedEvent;
import dev.langchain4j.observability.event.DefaultAiServiceStartedEvent;
import dev.langchain4j.observability.event.DefaultInputGuardrailExecutedEvent;
import dev.langchain4j.observability.event.DefaultOutputGuardrailExecutedEvent;
import dev.langchain4j.observability.event.DefaultToolExecutedEvent;
import dev.langchain4j.service.guardrail.InputGuardrails;
import dev.langchain4j.service.guardrail.OutputGuardrails;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.observability.AiServiceSelector;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.logging.Log;
import io.quarkus.test.QuarkusUnitTest;

class AuditingTests extends OpenAiBaseTest {
    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    private static final String SCENARIO = "tools";
    private static final String SECOND_SCENARIO = "second";
    private static final String USER_MESSAGE = "What is the square root of 485906798473894056 in scientific notation?";
    private static final String EXPECTED_RESPONSE = "The square root of 485,906,798,473,894,056 in scientific notation is approximately 6.97070153193991E8.";

    @Inject
    Assistant1 assistant1;

    @Inject
    Assistant2 assistant2;

    @Inject
    AllServicesAuditor allServicesAuditor;

    @Inject
    AllServicesInputGuardrailAuditor allServicesInputGuardrailAuditor;

    @Inject
    AllServicesOutputGuardrailAuditor allServicesOutputGuardrailAuditor;

    @Inject
    Assistant1ServicesAuditor assistant1ServicesAuditor;

    @Inject
    Assistant1InputGuardrailAuditor assistant1InputGuardrailAuditor;

    @Inject
    Assistant1OutputGuardrailAuditor assistant1OutputGuardrailAuditor;

    @Inject
    Assistant2ServicesAuditor assistant2ServicesAuditor;

    @Inject
    Assistant2InputGuardrailAuditor assistant2InputGuardrailAuditor;

    @Inject
    Assistant2OutputGuardrailAuditor assistant2OutputGuardrailAuditor;

    @Inject
    CalculatorAfter calculatorAfter;

    @BeforeEach
    void beforeEach() {
        resetSetup();
    }

    @ParameterizedTest
    @ValueSource(classes = { Assistant1.class, Assistant2.class })
    void should_audit_input_guardrail_events(Class<? extends Assistant> assistantClass) {
        shouldAuditInputGuardrailEvents(getAssistant(assistantClass), allServicesInputGuardrailAuditor);
    }

    @Test
    void should_not_audit_unselected_input_guardrail_events() {
        // invoking assistant1 should get events on both the all services auditor and the assistant1 specific auditor
        assertThatExceptionOfType(InputGuardrailException.class)
                .isThrownBy(() -> assistant1.chatWithInputGuardrails(USER_MESSAGE))
                .withMessage("The guardrail %s failed with this message: User message is not valid",
                        FailureInputGuardrail.class.getName());

        verifyAuditInputGuardrailEvents(allServicesInputGuardrailAuditor);
        verifyAuditInputGuardrailEvents(assistant1InputGuardrailAuditor);
        noEventsReceived(assistant2InputGuardrailAuditor);

        resetSetup();

        // invoking assistant2 should get events on both the all services auditor and the assistant2 specific auditor
        assertThatExceptionOfType(InputGuardrailException.class)
                .isThrownBy(() -> assistant2.chatWithInputGuardrails(USER_MESSAGE))
                .withMessage("The guardrail %s failed with this message: User message is not valid",
                        FailureInputGuardrail.class.getName());

        verifyAuditInputGuardrailEvents(allServicesInputGuardrailAuditor);
        verifyAuditInputGuardrailEvents(assistant2InputGuardrailAuditor);
        noEventsReceived(assistant1InputGuardrailAuditor);
    }

    @ParameterizedTest
    @ValueSource(classes = { Assistant1.class, Assistant2.class })
    void should_audit_output_guardrail_events(Class<? extends Assistant> assistantClass) {
        shouldAuditOutputGuardrailEvents(getAssistant(assistantClass), allServicesOutputGuardrailAuditor);
    }

    @Test
    void should_not_audit_unselected_output_guardrail_events() {
        // invoking assistant1 should get events on both the all services auditor and the assistant1 specific auditor
        assertThatExceptionOfType(OutputGuardrailException.class)
                .isThrownBy(() -> assistant1.chatWithOutputGuardrails(USER_MESSAGE))
                .withMessage("The guardrail %s failed with this message: LLM response is not valid",
                        FailureOutputGuardrail.class.getName());

        verifyAuditOutputGuardrailEvents(allServicesOutputGuardrailAuditor);
        verifyAuditOutputGuardrailEvents(assistant1OutputGuardrailAuditor);
        noEventsReceived(assistant2OutputGuardrailAuditor);

        resetSetup();

        // invoking assistant2 should get events on both the all services auditor and the assistant2 specific auditor
        assertThatExceptionOfType(OutputGuardrailException.class)
                .isThrownBy(() -> assistant2.chatWithOutputGuardrails(USER_MESSAGE))
                .withMessage("The guardrail %s failed with this message: LLM response is not valid",
                        FailureOutputGuardrail.class.getName());

        verifyAuditOutputGuardrailEvents(allServicesOutputGuardrailAuditor);
        verifyAuditOutputGuardrailEvents(assistant2OutputGuardrailAuditor);
        noEventsReceived(assistant1OutputGuardrailAuditor);
    }

    @ParameterizedTest
    @ValueSource(classes = { Assistant1.class, Assistant2.class })
    void should_execute_tool_then_answer(Class<? extends Assistant> assistantClass) throws IOException {
        shouldExecuteToolThenAnswer(assistantClass, getAssistant(assistantClass));
    }

    private void noEventsReceived(BaseAuditor auditor) {
        assertThat(wiremock().getServeEvents().size()).isGreaterThanOrEqualTo(2);
        assertThat(auditor.invocationContexts)
                .isNotNull()
                .isEmpty();

        assertThat(auditor.failed).isZero();
        assertThat(auditor.aiServiceStartedCounter).hasValue(0);
        assertThat(auditor.aiServiceCompletedCounter).hasValue(0);
        assertThat(auditor.aiServiceErrorCounter).hasValue(0);
        assertThat(auditor.aiResponseReceivedCounter).hasValue(0);
        assertThat(auditor.toolExecutedCounter).hasValue(0);
    }

    private static <E extends GuardrailExecutedEvent> void noEventsReceived(BaseGuardrailAuditor<E> auditor) {
        assertThat(auditor.events)
                .isNotNull()
                .isEmpty();
    }

    private static void shouldAuditInputGuardrailEvents(Assistant assistant,
            BaseGuardrailAuditor<InputGuardrailExecutedEvent> auditor) {

        assertThatExceptionOfType(InputGuardrailException.class)
                .isThrownBy(() -> assistant.chatWithInputGuardrails(USER_MESSAGE))
                .withMessage("The guardrail %s failed with this message: User message is not valid",
                        FailureInputGuardrail.class.getName());

        verifyAuditInputGuardrailEvents(auditor);
    }

    private static void verifyAuditInputGuardrailEvents(BaseGuardrailAuditor<InputGuardrailExecutedEvent> auditor) {
        assertThat(auditor.events)
                .hasSize(2)
                .satisfies(inputGuardrailExecutedEvent -> {
                    assertThat(inputGuardrailExecutedEvent)
                            .extracting(
                                    e -> e.invocationContext().methodName(),
                                    e -> e.request().userMessage().singleText(),
                                    e -> e.rewrittenUserMessage().singleText(),
                                    InputGuardrailExecutedEvent::guardrailClass)
                            .containsExactly(
                                    "chatWithInputGuardrails",
                                    USER_MESSAGE,
                                    "Success!!",
                                    SuccessInputGuardrail.class);

                    assertThat(inputGuardrailExecutedEvent.result())
                            .isSuccessful();
                }, atIndex(0))
                .satisfies(inputGuardrailExecutedEvent -> {
                    assertThat(inputGuardrailExecutedEvent)
                            .extracting(
                                    e -> e.invocationContext().methodName(),
                                    e -> e.request().userMessage().singleText(),
                                    e -> e.rewrittenUserMessage().singleText(),
                                    InputGuardrailExecutedEvent::guardrailClass)
                            .containsExactly(
                                    "chatWithInputGuardrails",
                                    "Success!!",
                                    "Success!!",
                                    FailureInputGuardrail.class);

                    assertThat(inputGuardrailExecutedEvent.result())
                            .hasSingleFailureWithMessage("User message is not valid");
                }, atIndex(1));
    }

    private static void shouldAuditOutputGuardrailEvents(Assistant assistant,
            BaseGuardrailAuditor<OutputGuardrailExecutedEvent> auditor) {
        assertThatExceptionOfType(OutputGuardrailException.class)
                .isThrownBy(() -> assistant.chatWithOutputGuardrails(USER_MESSAGE))
                .withMessage("The guardrail %s failed with this message: LLM response is not valid",
                        FailureOutputGuardrail.class.getName());

        verifyAuditOutputGuardrailEvents(auditor);
    }

    private static void verifyAuditOutputGuardrailEvents(BaseGuardrailAuditor<OutputGuardrailExecutedEvent> auditor) {
        assertThat(auditor.events)
                .hasSize(2)
                .satisfies(outputGuardrailExecutedEvent -> {
                    assertThat(outputGuardrailExecutedEvent)
                            .extracting(
                                    e -> e.invocationContext().methodName(),
                                    e -> e.request().responseFromLLM().aiMessage().text(),
                                    OutputGuardrailExecutedEvent::guardrailClass)
                            .containsExactly(
                                    "chatWithOutputGuardrails",
                                    EXPECTED_RESPONSE,
                                    SuccessOutputGuardrail.class);

                    assertThat(outputGuardrailExecutedEvent.result())
                            .hasSuccessfulText("Success!!");
                }, atIndex(0))
                .satisfies(outputGuardrailExecutedEvent -> {
                    assertThat(outputGuardrailExecutedEvent)
                            .extracting(
                                    e -> e.invocationContext().methodName(),
                                    e -> e.request().responseFromLLM().aiMessage().text(),
                                    OutputGuardrailExecutedEvent::guardrailClass)
                            .containsExactly(
                                    "chatWithOutputGuardrails",
                                    "Success!!",
                                    FailureOutputGuardrail.class);

                    assertThat(outputGuardrailExecutedEvent.result())
                            .hasSingleFailureWithMessage("LLM response is not valid");
                }, atIndex(1));
    }

    private void shouldExecuteToolThenAnswer(Class<? extends Assistant> assistantClass, Assistant assistant)
            throws IOException {

        var answer = assistant.chat(USER_MESSAGE);
        verifyToolExecutedEvents(assistantClass, answer, allServicesAuditor);

        if (assistantClass.getName().equals(Assistant1.class.getName())) {
            verifyToolExecutedEvents(Assistant1.class, answer, assistant1ServicesAuditor);
            noEventsReceived(assistant2ServicesAuditor);
        } else {
            verifyToolExecutedEvents(Assistant2.class, answer, assistant2ServicesAuditor);
            noEventsReceived(assistant1ServicesAuditor);
        }
    }

    private void verifyToolExecutedEvents(Class<? extends Assistant> assistantClass, String answer, BaseAuditor auditor)
            throws IOException {
        assertThat(answer).isEqualTo(EXPECTED_RESPONSE);

        var numServeEvents = wiremock().getServeEvents().size();
        assertThat(numServeEvents).isGreaterThanOrEqualTo(2);

        assertMultipleRequestMessage(
                getRequestAsMap(getRequestBody(wiremock().getServeEvents().get((numServeEvents == 2) ? 0 : 2))),
                List.of(
                        new MessageContent("system", "You are a chat bot that answers questions"),
                        new MessageContent("user",
                                "What is the square root of 485906798473894056 in scientific notation?"),
                        new MessageContent("assistant", null),
                        new MessageContent("function", "6.97070153193991E8")));

        assertThat(auditor.invocationContexts).hasSize(5);
        assertThat(auditor.invocationContexts.stream().collect(Collectors.toSet()))
                .singleElement()
                .extracting(
                        InvocationContext::interfaceName,
                        InvocationContext::methodName,
                        InvocationContext::methodArguments)
                .containsExactly(
                        assistantClass.getName(),
                        "chat",
                        List.of(USER_MESSAGE));

        assertThat(auditor.originalUserMessage).isEqualTo(USER_MESSAGE);
        assertThat(auditor.systemMessage).isEqualTo("You are a chat bot that answers questions");
        assertThat(auditor.aiMessageToolExecution).isEqualTo("squareRoot");
        assertThat(auditor.toolResult).isEqualTo("6.97070153193991E8");
        assertThat(auditor.result).isEqualTo(Optional.of(EXPECTED_RESPONSE));
        assertThat(auditor.failed).isZero();
        assertThat(auditor.aiServiceStartedCounter.get()).isGreaterThanOrEqualTo(1);
        assertThat(auditor.aiServiceCompletedCounter.get()).isGreaterThanOrEqualTo(1);
        assertThat(auditor.aiServiceErrorCounter.get()).isZero();
        assertThat(auditor.aiResponseReceivedCounter.get()).isGreaterThanOrEqualTo(1);
        assertThat(auditor.toolExecutedCounter.get()).isGreaterThanOrEqualTo(1);
    }

    private void resetSetup() {
        setupWiremock();
        this.allServicesAuditor.init();
        this.allServicesInputGuardrailAuditor.init();
        this.allServicesOutputGuardrailAuditor.init();
        this.assistant1ServicesAuditor.init();
        this.assistant1InputGuardrailAuditor.init();
        this.assistant1OutputGuardrailAuditor.init();
        this.assistant2ServicesAuditor.init();
        this.assistant2InputGuardrailAuditor.init();
        this.assistant2OutputGuardrailAuditor.init();
    }

    private <A extends Assistant> A getAssistant(Class<A> assistantClass) {
        if (Assistant1.class.getName().equals(assistantClass.getName())) {
            return (A) assistant1;
        } else if (Assistant2.class.getName().equals(assistantClass.getName())) {
            return (A) assistant2;
        } else {
            throw new IllegalArgumentException("Unknown assistant class: " + assistantClass);
        }
    }

    private void setupWiremock() {
        var firstResponse = """
                {
                  "id": "chatcmpl-8D88Dag1gAKnOPP9Ed4bos7vSpaNz",
                  "object": "chat.completion",
                  "created": 1698140213,
                  "model": "gpt-3.5-turbo-0613",
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": null,
                        "function_call": {
                          "name": "squareRoot",
                          "arguments": "{\\n  \\"number\\": 485906798473894056\\n}"
                        }
                      },
                      "finish_reason": "function_call"
                    }
                  ],
                  "usage": {
                    "prompt_tokens": 65,
                    "completion_tokens": 20,
                    "total_tokens": 85
                  }
                }
                """;

        var secondResponse = """
                        {
                          "id": "chatcmpl-8D88FIAUWSpwLaShFr0w8G1SWuVdl",
                          "object": "chat.completion",
                          "created": 1698140215,
                          "model": "gpt-3.5-turbo-0613",
                          "choices": [
                            {
                              "index": 0,
                              "message": {
                                "role": "assistant",
                                "content": "The square root of 485,906,798,473,894,056 in scientific notation is approximately 6.97070153193991E8."
                              },
                              "finish_reason": "stop"
                            }
                          ],
                          "usage": {
                            "prompt_tokens": 102,
                            "completion_tokens": 33,
                            "total_tokens": 135
                          }
                        }
                """;

        wiremock().register(
                post(urlPathEqualTo("/v1/chat/completions"))
                        .inScenario(SCENARIO)
                        .whenScenarioStateIs(Scenario.STARTED)
                        .willReturn(okJson(firstResponse)));
        wiremock().register(
                post(urlPathEqualTo("/v1/chat/completions"))
                        .inScenario(SCENARIO)
                        .whenScenarioStateIs(SECOND_SCENARIO)
                        .willReturn(okJson(secondResponse)));

        wiremock().setSingleScenarioState(SCENARIO, Scenario.STARTED);
    }

    @Singleton
    static class SuccessInputGuardrail implements InputGuardrail {
        @Override
        public InputGuardrailResult validate(UserMessage userMessage) {
            return successWith("Success!!");
        }
    }

    @Singleton
    static class FailureInputGuardrail implements InputGuardrail {
        @Override
        public InputGuardrailResult validate(UserMessage userMessage) {
            return failure("User message is not valid");
        }
    }

    @Singleton
    static class SuccessOutputGuardrail implements OutputGuardrail {
        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            return successWith("Success!!");
        }
    }

    @Singleton
    static class FailureOutputGuardrail implements OutputGuardrail {
        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            return failure("LLM response is not valid");
        }
    }

    @Singleton
    static class CalculatorAfter implements Runnable {
        private final WireMock wireMock;

        public CalculatorAfter(@ConfigProperty(name = "quarkus.wiremock.devservices.port") Integer wiremockPort) {
            this.wireMock = new WireMock(wiremockPort);
        }

        @Override
        public void run() {
            this.wireMock.setSingleScenarioState(SCENARIO, SECOND_SCENARIO);
        }
    }

    @dev.langchain4j.service.SystemMessage("You are a chat bot that answers questions")
    interface Assistant {
        String chat(String message);

        @InputGuardrails({ SuccessInputGuardrail.class, FailureInputGuardrail.class })
        String chatWithInputGuardrails(String message);

        @OutputGuardrails({ SuccessOutputGuardrail.class, FailureOutputGuardrail.class })
        String chatWithOutputGuardrails(String message);
    }

    @RegisterAiService(tools = Calculator.class)
    @Singleton
    interface Assistant1 extends Assistant {
    }

    @RegisterAiService(tools = Calculator.class)
    @Singleton
    interface Assistant2 extends Assistant {
    }

    @Singleton
    static class Calculator {
        private final CalculatorAfter after;

        Calculator(CalculatorAfter after) {
            this.after = after;
        }

        @Tool("calculates the square root of the provided number")
        double squareRoot(double number) {
            var result = Math.sqrt(number);
            after.run();
            return result;
        }
    }

    static abstract class BaseAuditor {
        private final Logger logger = Logger.getLogger(getClass());
        List<InvocationContext> invocationContexts = new ArrayList<>();
        String systemMessage;
        String originalUserMessage;
        String aiMessageToolExecution;
        String toolResult;
        String aiMessageResult;
        Object result;
        int failed;
        AtomicInteger aiServiceStartedCounter = new AtomicInteger(0);
        AtomicInteger aiServiceCompletedCounter = new AtomicInteger(0);
        AtomicInteger aiServiceErrorCounter = new AtomicInteger(0);
        AtomicInteger aiResponseReceivedCounter = new AtomicInteger(0);
        AtomicInteger toolExecutedCounter = new AtomicInteger(0);

        protected void init() {
            this.invocationContexts = new ArrayList<>();
            this.systemMessage = null;
            this.originalUserMessage = null;
            this.aiMessageToolExecution = null;
            this.toolResult = null;
            this.aiMessageResult = null;
            this.result = null;
            this.failed = 0;
            this.aiServiceStartedCounter.set(0);
            this.aiServiceCompletedCounter.set(0);
            this.aiServiceErrorCounter.set(0);
            this.aiResponseReceivedCounter.set(0);
            this.toolExecutedCounter.set(0);
        }

        protected void aiServiceStarted(AiServiceStartedEvent serviceStartedEvent) {
            assertThat(serviceStartedEvent)
                    .isNotNull()
                    .isExactlyInstanceOf(DefaultAiServiceStartedEvent.class);
            handle(serviceStartedEvent);

            if (captureEvent(serviceStartedEvent.invocationContext())) {
                this.aiServiceStartedCounter.incrementAndGet();
                this.systemMessage = serviceStartedEvent.systemMessage()
                        .map(SystemMessage::text)
                        .orElse(null);
                this.originalUserMessage = serviceStartedEvent.userMessage().singleText();
            }
        }

        protected void aiServiceCompleted(AiServiceCompletedEvent serviceCompletedEvent) {
            assertThat(serviceCompletedEvent)
                    .isNotNull()
                    .isExactlyInstanceOf(DefaultAiServiceCompletedEvent.class);
            handle(serviceCompletedEvent);

            if (captureEvent(serviceCompletedEvent.invocationContext())) {
                this.aiServiceCompletedCounter.incrementAndGet();
                this.result = serviceCompletedEvent.result();
            }
        }

        protected void aiServiceError(AiServiceErrorEvent serviceErrorEvent) {
            assertThat(serviceErrorEvent)
                    .isNotNull()
                    .isExactlyInstanceOf(DefaultAiServiceErrorEvent.class);
            handle(serviceErrorEvent);

            if (captureEvent(serviceErrorEvent.invocationContext())) {
                this.aiServiceErrorCounter.incrementAndGet();
                this.failed++;
            }
        }

        protected void serviceResponseReceived(AiServiceResponseReceivedEvent serviceResponseReceivedEvent) {
            assertThat(serviceResponseReceivedEvent)
                    .isNotNull()
                    .isExactlyInstanceOf(DefaultAiServiceResponseReceivedEvent.class);
            handle(serviceResponseReceivedEvent);

            if (captureEvent(serviceResponseReceivedEvent.invocationContext())) {
                this.aiResponseReceivedCounter.incrementAndGet();

                if (serviceResponseReceivedEvent.response().aiMessage().text() != null) {
                    this.aiMessageResult = serviceResponseReceivedEvent.response().aiMessage().text();
                } else {
                    this.aiMessageToolExecution = serviceResponseReceivedEvent.response().aiMessage().toolExecutionRequests()
                            .get(0)
                            .name();
                }
            }
        }

        protected void toolExecuted(ToolExecutedEvent toolExecutedEvent) {
            assertThat(toolExecutedEvent)
                    .isNotNull()
                    .isExactlyInstanceOf(DefaultToolExecutedEvent.class);
            handle(toolExecutedEvent);

            if (captureEvent(toolExecutedEvent.invocationContext())) {
                this.toolExecutedCounter.incrementAndGet();
                this.toolResult = toolExecutedEvent.resultText();
            }
        }

        private void handle(AiServiceEvent aiServiceEvent) {
            var invocationContext = aiServiceEvent.invocationContext();
            logger.infof("Got [%s] Event: %s", aiServiceEvent.eventClass().getSimpleName(), invocationContext);

            if (captureEvent(invocationContext)) {
                this.invocationContexts.add(invocationContext);
            }
        }

        private static boolean captureEvent(InvocationContext invocationContext) {
            return "chat".equals(invocationContext.methodName());
        }
    }

    @Singleton
    static class AllServicesAuditor extends BaseAuditor {
        @Override
        public void aiServiceStarted(@Observes AiServiceStartedEvent serviceStartedEvent) {
            super.aiServiceStarted(serviceStartedEvent);
        }

        @Override
        public void aiServiceCompleted(@Observes AiServiceCompletedEvent serviceCompletedEvent) {
            super.aiServiceCompleted(serviceCompletedEvent);
        }

        @Override
        public void aiServiceError(@Observes AiServiceErrorEvent serviceErrorEvent) {
            super.aiServiceError(serviceErrorEvent);
        }

        @Override
        public void serviceResponseReceived(@Observes AiServiceResponseReceivedEvent serviceResponseReceivedEvent) {
            super.serviceResponseReceived(serviceResponseReceivedEvent);
        }

        @Override
        public void toolExecuted(@Observes ToolExecutedEvent toolExecutedEvent) {
            super.toolExecuted(toolExecutedEvent);
        }
    }

    @Singleton
    static class Assistant1ServicesAuditor extends BaseAuditor {
        @Override
        public void aiServiceStarted(@Observes @AiServiceSelector(Assistant1.class) AiServiceStartedEvent serviceStartedEvent) {
            super.aiServiceStarted(serviceStartedEvent);
        }

        @Override
        public void aiServiceCompleted(
                @Observes @AiServiceSelector(Assistant1.class) AiServiceCompletedEvent serviceCompletedEvent) {
            super.aiServiceCompleted(serviceCompletedEvent);
        }

        @Override
        public void aiServiceError(@Observes @AiServiceSelector(Assistant1.class) AiServiceErrorEvent serviceErrorEvent) {
            super.aiServiceError(serviceErrorEvent);
        }

        @Override
        public void serviceResponseReceived(
                @Observes @AiServiceSelector(Assistant1.class) AiServiceResponseReceivedEvent serviceResponseReceivedEvent) {
            super.serviceResponseReceived(serviceResponseReceivedEvent);
        }

        @Override
        public void toolExecuted(@Observes @AiServiceSelector(Assistant1.class) ToolExecutedEvent toolExecutedEvent) {
            super.toolExecuted(toolExecutedEvent);
        }
    }

    @Singleton
    static class Assistant2ServicesAuditor extends BaseAuditor {
        @Override
        public void aiServiceStarted(@Observes @AiServiceSelector(Assistant2.class) AiServiceStartedEvent serviceStartedEvent) {
            super.aiServiceStarted(serviceStartedEvent);
        }

        @Override
        public void aiServiceCompleted(
                @Observes @AiServiceSelector(Assistant2.class) AiServiceCompletedEvent serviceCompletedEvent) {
            super.aiServiceCompleted(serviceCompletedEvent);
        }

        @Override
        public void aiServiceError(@Observes @AiServiceSelector(Assistant2.class) AiServiceErrorEvent serviceErrorEvent) {
            super.aiServiceError(serviceErrorEvent);
        }

        @Override
        public void serviceResponseReceived(
                @Observes @AiServiceSelector(Assistant2.class) AiServiceResponseReceivedEvent serviceResponseReceivedEvent) {
            super.serviceResponseReceived(serviceResponseReceivedEvent);
        }

        @Override
        public void toolExecuted(@Observes @AiServiceSelector(Assistant2.class) ToolExecutedEvent toolExecutedEvent) {
            super.toolExecuted(toolExecutedEvent);
        }
    }

    static abstract class BaseGuardrailAuditor<E extends GuardrailExecutedEvent> {
        List<E> events = new ArrayList<>();
        private final Class<? extends E> guardrailExecutedEventClass;
        private final String chatMethodName;

        protected BaseGuardrailAuditor(Class<? extends E> guardrailExecutedEventClass, String chatMethodName) {
            this.guardrailExecutedEventClass = guardrailExecutedEventClass;
            this.chatMethodName = chatMethodName;
        }

        protected void init() {
            this.events.clear();
        }

        protected void guardrailExecuted(E guardrailExecutedEvent) {
            assertThat(guardrailExecutedEvent)
                    .isNotNull()
                    .isExactlyInstanceOf(this.guardrailExecutedEventClass);
            handle(guardrailExecutedEvent);

            if (this.chatMethodName.equals(guardrailExecutedEvent.invocationContext().methodName())) {
                this.events.add(guardrailExecutedEvent);
            }
        }

        private static void handle(AiServiceEvent aiServiceEvent) {
            Log.infof("Got AI Service event: %s", aiServiceEvent.invocationContext());
        }
    }

    @Singleton
    static class AllServicesInputGuardrailAuditor extends BaseGuardrailAuditor<InputGuardrailExecutedEvent> {
        AllServicesInputGuardrailAuditor() {
            super(DefaultInputGuardrailExecutedEvent.class, "chatWithInputGuardrails");
        }

        public void inputGuardrailExecuted(@Observes InputGuardrailExecutedEvent inputGuardrailExecutedEvent) {
            guardrailExecuted(inputGuardrailExecutedEvent);
        }
    }

    @Singleton
    static class Assistant1InputGuardrailAuditor extends BaseGuardrailAuditor<InputGuardrailExecutedEvent> {
        Assistant1InputGuardrailAuditor() {
            super(DefaultInputGuardrailExecutedEvent.class, "chatWithInputGuardrails");
        }

        public void inputGuardrailExecuted(
                @Observes @AiServiceSelector(Assistant1.class) InputGuardrailExecutedEvent inputGuardrailExecutedEvent) {
            guardrailExecuted(inputGuardrailExecutedEvent);
        }
    }

    @Singleton
    static class Assistant2InputGuardrailAuditor extends BaseGuardrailAuditor<InputGuardrailExecutedEvent> {
        Assistant2InputGuardrailAuditor() {
            super(DefaultInputGuardrailExecutedEvent.class, "chatWithInputGuardrails");
        }

        public void inputGuardrailExecuted(
                @Observes @AiServiceSelector(Assistant2.class) InputGuardrailExecutedEvent inputGuardrailExecutedEvent) {
            guardrailExecuted(inputGuardrailExecutedEvent);
        }
    }

    @Singleton
    static class AllServicesOutputGuardrailAuditor extends BaseGuardrailAuditor<OutputGuardrailExecutedEvent> {
        AllServicesOutputGuardrailAuditor() {
            super(DefaultOutputGuardrailExecutedEvent.class, "chatWithOutputGuardrails");
        }

        public void outputGuardrailExecuted(@Observes OutputGuardrailExecutedEvent outputGuardrailExecutedEvent) {
            guardrailExecuted(outputGuardrailExecutedEvent);
        }
    }

    @Singleton
    static class Assistant1OutputGuardrailAuditor extends BaseGuardrailAuditor<OutputGuardrailExecutedEvent> {
        Assistant1OutputGuardrailAuditor() {
            super(DefaultOutputGuardrailExecutedEvent.class, "chatWithOutputGuardrails");
        }

        public void outputGuardrailExecuted(
                @Observes @AiServiceSelector(Assistant1.class) OutputGuardrailExecutedEvent outputGuardrailExecutedEvent) {
            guardrailExecuted(outputGuardrailExecutedEvent);
        }
    }

    @Singleton
    static class Assistant2OutputGuardrailAuditor extends BaseGuardrailAuditor<OutputGuardrailExecutedEvent> {
        Assistant2OutputGuardrailAuditor() {
            super(DefaultOutputGuardrailExecutedEvent.class, "chatWithOutputGuardrails");
        }

        public void outputGuardrailExecuted(
                @Observes @AiServiceSelector(Assistant2.class) OutputGuardrailExecutedEvent outputGuardrailExecutedEvent) {
            guardrailExecuted(outputGuardrailExecutedEvent);
        }
    }
}
