package io.quarkiverse.langchain4j.ollama.deployment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.tomakehurst.wiremock.stubbing.Scenario;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Multi;

public class OllamaStreamingChatLanguageModelSmokeTest extends WiremockAware {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClasses(Calculator.class))
            .overrideConfigKey("quarkus.langchain4j.ollama.base-url", WiremockAware.wiremockUrlForConfig())
            .overrideConfigKey("quarkus.langchain4j.devservices.enabled", "false");

    @Singleton
    @RegisterAiService(tools = Calculator.class)
    interface AIServiceWithTool {
        Multi<String> streaming(@MemoryId String memoryId, @dev.langchain4j.service.UserMessage String text);
    }

    @Singleton
    @RegisterAiService
    interface AIServiceWithoutTool {
        Multi<String> streaming(@dev.langchain4j.service.UserMessage String text);
    }

    @Singleton
    static class Calculator {
        @Tool("Execute the sum of two numbers")
        public int sum(int firstNumber, int secondNumber) {
            return firstNumber + secondNumber;
        }
    }

    @Inject
    AIServiceWithTool aiServiceWithTool;

    @Inject
    AIServiceWithoutTool aiServiceWithoutTool;

    @Inject
    ChatMemoryStore memory;

    @Test
    void test_1() {
        wiremock().register(
                post(urlEqualTo("/api/chat"))
                        .withRequestBody(equalToJson("""
                                {
                                  "model" : "llama3.2",
                                  "messages" : [ {
                                    "role" : "user",
                                    "content" : "Hello"
                                  }],
                                  "options" : {
                                    "temperature" : 0.8,
                                    "top_k" : 40,
                                    "top_p" : 0.9
                                  },
                                  "stream" : true
                                }
                                """))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/x-ndjson")
                                .withBody(
                                        """
                                                {"model":"llama3.2","created_at":"2024-11-30T09:03:42.312611426Z","message":{"role":"assistant","content":"Hello"},"done":false}
                                                {"model":"llama3.2","created_at":"2024-11-30T09:03:42.514215351Z","message":{"role":"assistant","content":"!"},"done":false}
                                                {"model":"llama3.2","created_at":"2024-11-30T09:03:44.109059873Z","message":{"role":"assistant","content":""},"done_reason":"stop","done":true,"total_duration":4821417857,"load_duration":2508844071,"prompt_eval_count":11,"prompt_eval_duration":514000000,"eval_count":10,"eval_duration":1797000000}""")));

        var result = aiServiceWithoutTool.streaming("Hello").collect().asList().await().indefinitely();
        assertEquals(List.of("Hello", "!"), result);
    }

    @Test
    void test_2() {
        wiremock().register(
                post(urlEqualTo("/api/chat"))
                        .withRequestBody(equalToJson("""
                                {
                                  "model" : "llama3.2",
                                  "messages" : [ {
                                    "role" : "user",
                                    "content" : "Hello"
                                  }],
                                  "tools" : [ {
                                    "type" : "function",
                                    "function" : {
                                      "name" : "sum",
                                      "description" : "Execute the sum of two numbers",
                                      "parameters" : {
                                        "type" : "object",
                                        "properties" : {
                                          "firstNumber" : {
                                            "type" : "integer"
                                          },
                                          "secondNumber" : {
                                            "type" : "integer"
                                          }
                                        },
                                        "required" : [ "firstNumber", "secondNumber" ]
                                      }
                                    }
                                  } ],
                                  "options" : {
                                    "temperature" : 0.8,
                                    "top_k" : 40,
                                    "top_p" : 0.9
                                  },
                                  "stream" : true
                                }
                                """))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/x-ndjson")
                                .withBody(
                                        """
                                                {"model":"llama3.2","created_at":"2024-11-30T09:03:42.312611426Z","message":{"role":"assistant","content":"Hello"},"done":false}
                                                {"model":"llama3.2","created_at":"2024-11-30T09:03:42.514215351Z","message":{"role":"assistant","content":"!"},"done":false}
                                                {"model":"llama3.2","created_at":"2024-11-30T09:03:44.109059873Z","message":{"role":"assistant","content":""},"done_reason":"stop","done":true,"total_duration":4821417857,"load_duration":2508844071,"prompt_eval_count":11,"prompt_eval_duration":514000000,"eval_count":10,"eval_duration":1797000000}""")));

        var result = aiServiceWithTool.streaming("1", "Hello").collect().asList().await().indefinitely();
        assertEquals(List.of("Hello", "!"), result);
    }

    @Test
    void test_3() {
        wiremock()
                .register(
                        post(urlEqualTo("/api/chat"))
                                .inScenario("")
                                .whenScenarioStateIs(Scenario.STARTED)
                                .willSetStateTo("TOOL_CALL")
                                .withRequestBody(equalToJson("""
                                        {
                                          "model" : "llama3.2",
                                          "messages" : [ {
                                            "role" : "user",
                                            "content" : "1 + 1"
                                          }],
                                          "tools" : [ {
                                            "type" : "function",
                                            "function" : {
                                              "name" : "sum",
                                              "description" : "Execute the sum of two numbers",
                                              "parameters" : {
                                                "type" : "object",
                                                "properties" : {
                                                  "firstNumber" : {
                                                    "type" : "integer"
                                                  },
                                                  "secondNumber" : {
                                                    "type" : "integer"
                                                  }
                                                },
                                                "required" : [ "firstNumber", "secondNumber" ]
                                              }
                                            }
                                          } ],
                                          "options" : {
                                            "temperature" : 0.8,
                                            "top_k" : 40,
                                            "top_p" : 0.9
                                          },
                                          "stream" : true
                                        }
                                        """))

                                .willReturn(aResponse()
                                        .withHeader("Content-Type", "application/x-ndjson")
                                        .withBody(
                                                """
                                                        {"model":"llama3.1","created_at":"2024-11-30T16:36:02.833930413Z","message":{"role":"assistant","content":"","tool_calls":[{"function":{"name":"sum","arguments":{"firstNumber":1,"secondNumber":1}}}]},"done":false}
                                                        {"model":"llama3.1","created_at":"2024-11-30T16:36:04.368016152Z","message":{"role":"assistant","content":""},"done_reason":"stop","done":true,"total_duration":28825672145,"load_duration":29961281,"prompt_eval_count":169,"prompt_eval_duration":3906000000,"eval_count":22,"eval_duration":24887000000}""")));

        wiremock()
                .register(
                        post(urlEqualTo("/api/chat"))
                                .inScenario("")
                                .whenScenarioStateIs("TOOL_CALL")
                                .willSetStateTo("AI_RESPONSE")
                                .withRequestBody(equalToJson("""
                                        {
                                          "model" : "llama3.2",
                                          "messages" : [ {
                                            "role" : "user",
                                            "content" : "1 + 1"
                                          }, {
                                            "role" : "assistant",
                                            "tool_calls" : [ {
                                              "function" : {
                                                "name" : "sum",
                                                "arguments" : {
                                                  "firstNumber" : 1,
                                                  "secondNumber" : 1
                                                }
                                              }
                                            } ]
                                          }, {
                                            "role" : "tool",
                                            "content" : "2"
                                          } ],
                                          "tools" : [ {
                                            "type" : "function",
                                            "function" : {
                                              "name" : "sum",
                                              "description" : "Execute the sum of two numbers",
                                              "parameters" : {
                                                "type" : "object",
                                                "properties" : {
                                                  "firstNumber" : {
                                                    "type" : "integer"
                                                  },
                                                  "secondNumber" : {
                                                    "type" : "integer"
                                                  }
                                                },
                                                "required" : [ "firstNumber", "secondNumber" ]
                                              }
                                            }
                                          } ],
                                          "options" : {
                                            "temperature" : 0.8,
                                            "top_k" : 40,
                                            "top_p" : 0.9
                                          },
                                          "stream" : true
                                        }
                                        """))
                                .willReturn(aResponse()
                                        .withHeader("Content-Type", "application/x-ndjson")
                                        .withBody(
                                                """
                                                        {"model":"llama3.1","created_at":"2024-11-30T16:36:04.368016152Z","message":{"role":"assistant","content":"The result is 2"},"done_reason":"stop","done":true,"total_duration":28825672145,"load_duration":29961281,"prompt_eval_count":169,"prompt_eval_duration":3906000000,"eval_count":22,"eval_duration":24887000000}""")));

        var result = aiServiceWithTool.streaming("2", "1 + 1").collect().asList().await().indefinitely();
        assertEquals(List.of("The result is 2"), result);

        var messages = memory.getMessages("2");
        assertEquals("1 + 1", ((dev.langchain4j.data.message.UserMessage) messages.get(0)).singleText());
        assertEquals("The result is 2", ((dev.langchain4j.data.message.AiMessage) messages.get(3)).text());

        if (messages.get(1) instanceof AiMessage aiMessage) {
            assertTrue(aiMessage.hasToolExecutionRequests());
            assertEquals("{\"firstNumber\":1,\"secondNumber\":1}", aiMessage.toolExecutionRequests().get(0).arguments());
        } else {
            fail("The second message is not of type AiMessage");
        }

        if (messages.get(2) instanceof ToolExecutionResultMessage toolResultMessage) {
            assertEquals(2, Integer.parseInt(toolResultMessage.text()));
        } else {
            fail("The third message is not of type ToolExecutionResultMessage");
        }
    }
}
