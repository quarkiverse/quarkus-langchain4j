package org.acme.examples.aiservices;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.quarkiverse.langchain4j.guardrails.GuardrailAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.atIndex;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.stubbing.Scenario;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.audit.AuditSourceInfo;
import io.quarkiverse.langchain4j.audit.InitialMessagesCreatedEvent;
import io.quarkiverse.langchain4j.audit.InputGuardrailExecutedEvent;
import io.quarkiverse.langchain4j.audit.LLMInteractionCompleteEvent;
import io.quarkiverse.langchain4j.audit.LLMInteractionEvent;
import io.quarkiverse.langchain4j.audit.LLMInteractionFailureEvent;
import io.quarkiverse.langchain4j.audit.OutputGuardrailExecutedEvent;
import io.quarkiverse.langchain4j.audit.ResponseFromLLMReceivedEvent;
import io.quarkiverse.langchain4j.audit.ToolExecutedEvent;
import io.quarkiverse.langchain4j.audit.internal.DefaultInitialMessagesCreatedEvent;
import io.quarkiverse.langchain4j.audit.internal.DefaultInputGuardrailExecutedEvent;
import io.quarkiverse.langchain4j.audit.internal.DefaultLLMInteractionCompleteEvent;
import io.quarkiverse.langchain4j.audit.internal.DefaultLLMInteractionFailureEvent;
import io.quarkiverse.langchain4j.audit.internal.DefaultOutputGuardrailExecutedEvent;
import io.quarkiverse.langchain4j.audit.internal.DefaultResponseFromLLMReceivedEvent;
import io.quarkiverse.langchain4j.audit.internal.DefaultToolExecutedEvent;
import io.quarkiverse.langchain4j.guardrails.InputGuardrail;
import io.quarkiverse.langchain4j.guardrails.InputGuardrailResult;
import io.quarkiverse.langchain4j.guardrails.InputGuardrails;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrail;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrailResult;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrails;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.runtime.aiservice.GuardrailException;
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
    Assistant assistant;

    @Inject
    Auditor auditor;

    @Inject
    InputGuardrailAuditor inputGuardrailAuditor;

    @Inject
    OutputGuardrailAuditor outputGuardrailAuditor;

    @Test
    void should_audit_input_guardrail_events() {
        setupWiremock();

        assertThatExceptionOfType(GuardrailException.class)
                .isThrownBy(() -> assistant.chatWithInputGuardrails(USER_MESSAGE))
                .withMessage("The guardrail %s failed with this message: User message is not valid",
                        FailureInputGuardrail.class.getName());

        assertThat(inputGuardrailAuditor.inputGuardrailExecutedEvents)
                .hasSize(2)
                .satisfies(inputGuardrailExecutedEvent -> {
                    assertThat(inputGuardrailExecutedEvent)
                            .extracting(
                                    e -> e.sourceInfo().methodName(),
                                    e -> e.params().userMessage().singleText(),
                                    e -> e.rewrittenUserMessage().singleText(),
                                    InputGuardrailExecutedEvent::guardrailClass)
                            .containsExactly(
                                    "chatWithInputGuardrails",
                                    USER_MESSAGE,
                                    "Success!!",
                                    SuccessInputGuardrail.class);

                    assertThat(inputGuardrailExecutedEvent.result()).isSuccessful();
                }, atIndex(0))
                .satisfies(inputGuardrailExecutedEvent -> {
                    assertThat(inputGuardrailExecutedEvent)
                            .extracting(
                                    e -> e.sourceInfo().methodName(),
                                    e -> e.params().userMessage().singleText(),
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

    @Test
    void should_audit_output_guardrail_events() {
        setupWiremock();

        assertThatExceptionOfType(GuardrailException.class)
                .isThrownBy(() -> assistant.chatWithOutputGuardrails(USER_MESSAGE))
                .withMessage("The guardrail %s failed with this message: LLM response is not valid",
                        FailureOutputGuardrail.class.getName());

        assertThat(outputGuardrailAuditor.outputGuardrailExecutedEvents)
                .hasSize(2)
                .satisfies(outputGuardrailExecutedEvent -> {
                    assertThat(outputGuardrailExecutedEvent)
                            .extracting(
                                    e -> e.sourceInfo().methodName(),
                                    e -> e.params().responseFromLLM().text(),
                                    OutputGuardrailExecutedEvent::guardrailClass)
                            .containsExactly(
                                    "chatWithOutputGuardrails",
                                    EXPECTED_RESPONSE,
                                    SuccessOutputGuardrail.class);

                    assertThat(outputGuardrailExecutedEvent.result())
                            .hasSuccess("Success!!", "Success!!");
                }, atIndex(0))
                .satisfies(outputGuardrailExecutedEvent -> {
                    assertThat(outputGuardrailExecutedEvent)
                            .extracting(
                                    e -> e.sourceInfo().methodName(),
                                    e -> e.params().responseFromLLM().text(),
                                    OutputGuardrailExecutedEvent::guardrailClass)
                            .containsExactly(
                                    "chatWithOutputGuardrails",
                                    "Success!!",
                                    FailureOutputGuardrail.class);

                    assertThat(outputGuardrailExecutedEvent.result())
                            .hasSingleFailureWithMessage("LLM response is not valid");
                }, atIndex(1));
    }

    @Test
    void should_execute_tool_then_answer() throws IOException {
        setupWiremock();

        var answer = assistant.chat(USER_MESSAGE);

        assertThat(answer).isEqualTo(EXPECTED_RESPONSE);
        assertThat(wiremock().getServeEvents()).hasSize(2);

        assertMultipleRequestMessage(getRequestAsMap(getRequestBody(wiremock().getServeEvents().get(0))),
                List.of(
                        new MessageContent("system", "You are a chat bot that answers questions"),
                        new MessageContent("user",
                                "What is the square root of 485906798473894056 in scientific notation?"),
                        new MessageContent("assistant", null),
                        new MessageContent("function", "6.97070153193991E8")));

        assertThat(auditor.auditSourceInfos).hasSize(5);
        assertThat(auditor.auditSourceInfos.stream().collect(Collectors.toSet()))
                .singleElement()
                .extracting(
                        AuditSourceInfo::interfaceName,
                        AuditSourceInfo::methodName,
                        AuditSourceInfo::methodParams)
                .containsExactly(
                        Assistant.class.getName(),
                        "chat",
                        new Object[] { USER_MESSAGE });

        assertThat(auditor.originalUserMessage).isEqualTo(USER_MESSAGE);
        assertThat(auditor.systemMessage).isEqualTo("You are a chat bot that answers questions");
        assertThat(auditor.aiMessageToolExecution).isEqualTo("squareRoot");
        assertThat(auditor.toolResult).isEqualTo("6.97070153193991E8");
        assertThat(auditor.result).isEqualTo(EXPECTED_RESPONSE);
        assertThat(auditor.failed).isZero();
        assertThat(auditor.initialMessagesCreatedCounter.get()).isGreaterThanOrEqualTo(1);
        assertThat(auditor.llmInteractionCompleteCounter.get()).isGreaterThanOrEqualTo(1);
        assertThat(auditor.llmInteractionFailedCounter.get()).isZero();
        assertThat(auditor.responseFromLLMReceivedCounter.get()).isGreaterThanOrEqualTo(1);
        assertThat(auditor.toolExecutedCounter.get()).isGreaterThanOrEqualTo(1);
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
        private final Integer wiremockPort;

        public CalculatorAfter(@ConfigProperty(name = "quarkus.wiremock.devservices.port") Integer wiremockPort) {
            this.wiremockPort = wiremockPort;
        }

        @Override
        public void run() {
            WireMock wireMock = new WireMock(wiremockPort);
            wireMock.setSingleScenarioState(SCENARIO, SECOND_SCENARIO);
        }
    }

    @RegisterAiService(tools = Calculator.class)
    @Singleton
    @dev.langchain4j.service.SystemMessage("You are a chat bot that answers questions")
    interface Assistant {
        String chat(String message);

        @InputGuardrails({ SuccessInputGuardrail.class, FailureInputGuardrail.class })
        String chatWithInputGuardrails(String message);

        @OutputGuardrails({ SuccessOutputGuardrail.class, FailureOutputGuardrail.class })
        String chatWithOutputGuardrails(String message);
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

    @Singleton
    static class Auditor {
        List<AuditSourceInfo> auditSourceInfos = new ArrayList<>();
        String systemMessage;
        String originalUserMessage;
        String aiMessageToolExecution;
        String toolResult;
        String aiMessageResult;
        Object result;
        int failed;
        AtomicInteger initialMessagesCreatedCounter = new AtomicInteger(0);
        AtomicInteger llmInteractionCompleteCounter = new AtomicInteger(0);
        AtomicInteger llmInteractionFailedCounter = new AtomicInteger(0);
        AtomicInteger responseFromLLMReceivedCounter = new AtomicInteger(0);
        AtomicInteger toolExecutedCounter = new AtomicInteger(0);

        public void initialMessagesCreated(@Observes InitialMessagesCreatedEvent initialMessagesCreatedEvent) {
            assertThat(initialMessagesCreatedEvent)
                    .isNotNull()
                    .isExactlyInstanceOf(DefaultInitialMessagesCreatedEvent.class);
            handle(initialMessagesCreatedEvent);

            if (captureEvent(initialMessagesCreatedEvent.sourceInfo())) {
                this.initialMessagesCreatedCounter.incrementAndGet();
                this.systemMessage = initialMessagesCreatedEvent.systemMessage()
                        .map(SystemMessage::text)
                        .orElse(null);
                this.originalUserMessage = initialMessagesCreatedEvent.userMessage().singleText();
            }
        }

        public void llmInteractionComplete(@Observes LLMInteractionCompleteEvent llmInteractionCompleteEvent) {
            assertThat(llmInteractionCompleteEvent)
                    .isNotNull()
                    .isExactlyInstanceOf(DefaultLLMInteractionCompleteEvent.class);
            handle(llmInteractionCompleteEvent);

            if (captureEvent(llmInteractionCompleteEvent.sourceInfo())) {
                this.llmInteractionCompleteCounter.incrementAndGet();
                this.result = llmInteractionCompleteEvent.result();
            }
        }

        public void llmInteractionFailed(@Observes LLMInteractionFailureEvent llmInteractionFailureEvent) {
            assertThat(llmInteractionFailureEvent)
                    .isNotNull()
                    .isExactlyInstanceOf(DefaultLLMInteractionFailureEvent.class);
            handle(llmInteractionFailureEvent);

            if (captureEvent(llmInteractionFailureEvent.sourceInfo())) {
                this.llmInteractionFailedCounter.incrementAndGet();
                this.failed++;
            }
        }

        public void responseFromLLMReceived(@Observes ResponseFromLLMReceivedEvent responseFromLLMReceivedEvent) {
            assertThat(responseFromLLMReceivedEvent)
                    .isNotNull()
                    .isExactlyInstanceOf(DefaultResponseFromLLMReceivedEvent.class);
            handle(responseFromLLMReceivedEvent);

            if (captureEvent(responseFromLLMReceivedEvent.sourceInfo())) {
                this.responseFromLLMReceivedCounter.incrementAndGet();

                if (responseFromLLMReceivedEvent.response().aiMessage().text() != null) {
                    this.aiMessageResult = responseFromLLMReceivedEvent.response().aiMessage().text();
                } else {
                    this.aiMessageToolExecution = responseFromLLMReceivedEvent.response().aiMessage().toolExecutionRequests()
                            .get(0)
                            .name();
                }
            }
        }

        public void toolExecuted(@Observes ToolExecutedEvent toolExecutedEvent) {
            assertThat(toolExecutedEvent)
                    .isNotNull()
                    .isExactlyInstanceOf(DefaultToolExecutedEvent.class);
            handle(toolExecutedEvent);

            if (captureEvent(toolExecutedEvent.sourceInfo())) {
                this.toolExecutedCounter.incrementAndGet();
                this.toolResult = toolExecutedEvent.result();
            }
        }

        private void handle(LLMInteractionEvent llmInteractionEvent) {
            var sourceInfo = llmInteractionEvent.sourceInfo();
            Log.infof("Got LLM interaction: %s", sourceInfo);

            if (captureEvent(sourceInfo)) {
                this.auditSourceInfos.add(sourceInfo);
            }
        }

        private static boolean captureEvent(AuditSourceInfo sourceInfo) {
            return "chat".equals(sourceInfo.methodName());
        }
    }

    @Singleton
    static class InputGuardrailAuditor {
        List<InputGuardrailExecutedEvent> inputGuardrailExecutedEvents = new ArrayList<>();

        public void inputGuardrailExecuted(@Observes InputGuardrailExecutedEvent inputGuardrailExecutedEvent) {
            assertThat(inputGuardrailExecutedEvent)
                    .isNotNull()
                    .isExactlyInstanceOf(DefaultInputGuardrailExecutedEvent.class);
            handle(inputGuardrailExecutedEvent);

            if ("chatWithInputGuardrails".equals(inputGuardrailExecutedEvent.sourceInfo().methodName())) {
                this.inputGuardrailExecutedEvents.add(inputGuardrailExecutedEvent);
            }
        }

        private static void handle(LLMInteractionEvent llmInteractionEvent) {
            Log.infof("Got LLM interaction: %s", llmInteractionEvent.sourceInfo());
        }
    }

    @Singleton
    static class OutputGuardrailAuditor {
        List<OutputGuardrailExecutedEvent> outputGuardrailExecutedEvents = new ArrayList<>();

        public void outputGuardrailExecuted(@Observes OutputGuardrailExecutedEvent outputGuardrailExecutedEvent) {
            assertThat(outputGuardrailExecutedEvent)
                    .isNotNull()
                    .isExactlyInstanceOf(DefaultOutputGuardrailExecutedEvent.class);
            handle(outputGuardrailExecutedEvent);

            if ("chatWithOutputGuardrails".equals(outputGuardrailExecutedEvent.sourceInfo().methodName())) {
                this.outputGuardrailExecutedEvents.add(outputGuardrailExecutedEvent);
            }
        }

        private static void handle(LLMInteractionEvent llmInteractionEvent) {
            Log.infof("Got LLM interaction: %s", llmInteractionEvent.sourceInfo());
        }
    }
}
