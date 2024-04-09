package org.acme.examples.aiservices;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

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
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.audit.Audit;
import io.quarkiverse.langchain4j.audit.AuditService;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.arc.Arc;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.test.QuarkusUnitTest;

public class AuditingServiceTest extends OpenAiBaseTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    private static final String scenario = "tools";
    private static final String secondState = "second";

    @Singleton
    public static class CalculatorAfter implements Runnable {

        private final Integer wiremockPort;

        public CalculatorAfter(@ConfigProperty(name = "quarkus.wiremock.devservices.port") Integer wiremockPort) {
            this.wiremockPort = wiremockPort;
        }

        @Override
        public void run() {
            WireMock wireMock = new WireMock(wiremockPort);
            wireMock.setSingleScenarioState(scenario, secondState);
        }
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

    @RegisterAiService(tools = Calculator.class)
    @Singleton
    interface Assistant {

        String chat(String message);
    }

    @Inject
    Assistant assistant;

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
                post(urlEqualTo("/v1/chat/completions"))
                        .inScenario(scenario)
                        .whenScenarioStateIs(Scenario.STARTED)
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody(firstResponse)));
        wiremock().register(
                post(urlEqualTo("/v1/chat/completions"))
                        .inScenario(scenario)
                        .whenScenarioStateIs(secondState)
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody(secondResponse)));

        wiremock().setSingleScenarioState(scenario, Scenario.STARTED);

        String userMessage = "What is the square root of 485906798473894056 in scientific notation?";

        String answer = assistant.chat(userMessage);

        String expectedResult = "The square root of 485,906,798,473,894,056 in scientific notation is approximately 6.97070153193991E8.";
        assertThat(answer).isEqualTo(expectedResult);

        assertThat(wiremock().getServeEvents()).hasSize(2);

        assertSingleRequestMessage(getRequestAsMap(getRequestBody(wiremock().getServeEvents().get(1))),
                "What is the square root of 485906798473894056 in scientific notation?");
        assertMultipleRequestMessage(getRequestAsMap(getRequestBody(wiremock().getServeEvents().get(0))),
                List.of(
                        new MessageContent("user",
                                "What is the square root of 485906798473894056 in scientific notation?"),
                        new MessageContent("assistant", null),
                        new MessageContent("function", "6.97070153193991E8")));

        InstanceHandle<SimpleAuditService> auditServiceInstance = Arc.container().instance(SimpleAuditService.class);
        assertTrue(auditServiceInstance.isAvailable());
        SimpleAuditService auditService = auditServiceInstance.get();
        SimpleAuditService.SimpleAudit audit = auditService.audit;
        assertThat(audit).isNotNull();
        assertThat(audit.originalUserMessage).isEqualTo(userMessage);
        assertThat(audit.systemMessage).isNull();
        assertThat(audit.aiMessageToolExecution).isEqualTo("squareRoot");
        assertThat(audit.toolResult).isEqualTo("6.97070153193991E8");
        assertThat(audit.result).isEqualTo(expectedResult);
        assertThat(audit.failed).isZero();
        assertThat(audit.failed).isZero();

    }

    @Singleton
    public static class SimpleAuditService implements AuditService {

        SimpleAudit audit;

        @Override
        public Audit create(Audit.CreateInfo createInfo) {
            return new SimpleAudit(createInfo);
        }

        @Override
        public void complete(Audit audit) {
            this.audit = (SimpleAudit) audit;
        }

        public static class SimpleAudit extends Audit {

            String systemMessage;
            String originalUserMessage;
            String aiMessageToolExecution;
            String toolResult;
            String aiMessageResult;
            Object result;

            int relevantDocs;
            int failed;

            public SimpleAudit(CreateInfo createInfo) {
                super(createInfo);
            }

            @Override
            public void initialMessages(Optional<SystemMessage> systemMessage, UserMessage userMessage) {
                if (systemMessage.isPresent()) {
                    this.systemMessage = systemMessage.get().text();
                }
                originalUserMessage = userMessage.text();
            }

            @Override
            public void addRelevantDocument(List<TextSegment> segments, UserMessage userMessage) {
                relevantDocs++;
            }

            @Override
            public void addLLMToApplicationMessage(Response<AiMessage> response) {
                if (response.content().text() != null) {
                    aiMessageResult = response.content().text();
                } else {
                    aiMessageToolExecution = response.content().toolExecutionRequests().get(0).name();
                }
            }

            @Override
            public void addApplicationToLLMMessage(ToolExecutionResultMessage toolExecutionResultMessage) {
                toolResult = toolExecutionResultMessage.text();
            }

            @Override
            public void onCompletion(Object result) {
                this.result = result;
            }

            @Override
            public void onFailure(Exception e) {
                failed++;
            }
        }
    }
}
