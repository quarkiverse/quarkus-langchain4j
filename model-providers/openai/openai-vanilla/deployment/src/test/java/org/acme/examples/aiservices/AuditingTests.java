package org.acme.examples.aiservices;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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
import dev.langchain4j.data.message.SystemMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.audit.AuditSourceInfo;
import io.quarkiverse.langchain4j.audit.InitialMessagesCreatedEvent;
import io.quarkiverse.langchain4j.audit.LLMInteractionCompleteEvent;
import io.quarkiverse.langchain4j.audit.LLMInteractionEvent;
import io.quarkiverse.langchain4j.audit.LLMInteractionFailureEvent;
import io.quarkiverse.langchain4j.audit.ResponseFromLLMReceivedEvent;
import io.quarkiverse.langchain4j.audit.ToolExecutedEvent;
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

    @Inject
    Assistant assistant;

    @Inject
    Auditor auditor;

    @Test
    void should_execute_tool_then_answer() throws IOException {
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

        var userMessage = "What is the square root of 485906798473894056 in scientific notation?";
        var answer = assistant.chat(userMessage);
        var expectedResult = "The square root of 485,906,798,473,894,056 in scientific notation is approximately 6.97070153193991E8.";

        assertThat(answer).isEqualTo(expectedResult);
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
                        new Object[] { userMessage });

        assertThat(auditor.originalUserMessage).isEqualTo(userMessage);
        assertThat(auditor.systemMessage).isEqualTo("You are a chat bot that answers questions");
        assertThat(auditor.aiMessageToolExecution).isEqualTo("squareRoot");
        assertThat(auditor.toolResult).isEqualTo("6.97070153193991E8");
        assertThat(auditor.result).isEqualTo(expectedResult);
        assertThat(auditor.failed).isZero();
    }

    @Singleton
    public static class CalculatorAfter implements Runnable {
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
    interface Assistant {
        @dev.langchain4j.service.SystemMessage("You are a chat bot that answers questions")
        String chat(String message);
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
    public static class Auditor {
        List<AuditSourceInfo> auditSourceInfos = new ArrayList<>();
        String systemMessage;
        String originalUserMessage;
        String aiMessageToolExecution;
        String toolResult;
        String aiMessageResult;
        Object result;
        int failed;

        public void initialMessagesCreated(@Observes InitialMessagesCreatedEvent initialMessagesCreatedEvent) {
            handle(initialMessagesCreatedEvent);
            this.systemMessage = initialMessagesCreatedEvent.systemMessage()
                    .map(SystemMessage::text)
                    .orElse(null);
            this.originalUserMessage = initialMessagesCreatedEvent.userMessage().singleText();
            this.auditSourceInfos.add(initialMessagesCreatedEvent.sourceInfo());
        }

        public void llmInteractionComplete(@Observes LLMInteractionCompleteEvent llmInteractionCompleteEvent) {
            handle(llmInteractionCompleteEvent);
            this.result = llmInteractionCompleteEvent.result();
            this.auditSourceInfos.add(llmInteractionCompleteEvent.sourceInfo());
        }

        public void llmInteractionFailed(@Observes LLMInteractionFailureEvent llmInteractionFailureEvent) {
            handle(llmInteractionFailureEvent);
            this.failed++;
            this.auditSourceInfos.add(llmInteractionFailureEvent.sourceInfo());
        }

        public void responseFromLLMReceived(@Observes ResponseFromLLMReceivedEvent responseFromLLMReceivedEvent) {
            handle(responseFromLLMReceivedEvent);
            this.auditSourceInfos.add(responseFromLLMReceivedEvent.sourceInfo());

            if (responseFromLLMReceivedEvent.response().aiMessage().text() != null) {
                this.aiMessageResult = responseFromLLMReceivedEvent.response().aiMessage().text();
            } else {
                this.aiMessageToolExecution = responseFromLLMReceivedEvent.response().aiMessage().toolExecutionRequests().get(0)
                        .name();
            }
        }

        public void toolExecuted(@Observes ToolExecutedEvent toolExecutedEvent) {
            handle(toolExecutedEvent);
            this.auditSourceInfos.add(toolExecutedEvent.sourceInfo());
            this.toolResult = toolExecutedEvent.result();
        }

        private static void handle(LLMInteractionEvent llmInteractionEvent) {
            Log.infof("Got LLM interaction: %s", llmInteractionEvent.sourceInfo());
        }
    }
}
