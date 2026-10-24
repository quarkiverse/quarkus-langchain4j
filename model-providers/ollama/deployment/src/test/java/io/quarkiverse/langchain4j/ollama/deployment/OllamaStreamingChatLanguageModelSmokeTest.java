package io.quarkiverse.langchain4j.ollama.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkiverse.langchain4j.mockapi.RawResponse;
import io.quarkiverse.langchain4j.runtime.aiservice.ChatEvent;
import io.quarkus.test.QuarkusExtensionTest;
import io.smallrye.mutiny.Multi;

public class OllamaStreamingChatLanguageModelSmokeTest {

    private static final String SUM_TOOL = """
            {
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
            }
            """;

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClasses(Calculator.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
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

        Multi<ChatEvent> streamingEvents(@dev.langchain4j.service.UserMessage String text);
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

    @Inject
    MockModelServer mock;

    @BeforeEach
    void reset() {
        mock.reset();
    }

    @Test
    void test_1() throws Exception {
        mock.chat().reply(ChatReply.text("Hello!").chunks(List.of("Hello", "!")));

        var result = aiServiceWithoutTool.streaming("Hello").collect().asList().await().indefinitely();
        assertEquals(List.of("Hello", "!"), result);

        CapturedRequest request = mock.chat().singleRequest();
        assertThat(request.path()).isEqualTo("/ollama/api/chat");
        assertThat(request.bodyAsJson()).isEqualTo(new ObjectMapper().readTree("""
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
                """));
    }

    @Test
    void emitsThinkingEvents() {
        // raw stub: the thinking arrives in two chunks, while a neutral reply streams its thinking in one object
        mock.raw().on("POST", "/ollama/api/chat").replies(RawResponse.of(200, Map.of("Content-Type", "application/x-ndjson"),
                """
                        {"model":"qwen3:1.7b","created_at":"2024-12-11T15:21:23.422542932Z","message":{"role":"assistant","content":"","thinking":"Let me "},"done":false}
                        {"model":"qwen3:1.7b","created_at":"2024-12-11T15:21:23.522542932Z","message":{"role":"assistant","content":"","thinking":"think."},"done":false}
                        {"model":"qwen3:1.7b","created_at":"2024-12-11T15:21:23.622542932Z","message":{"role":"assistant","content":"Hello"},"done":false}
                        {"model":"qwen3:1.7b","created_at":"2024-12-11T15:21:23.722542932Z","message":{"role":"assistant","content":"!"},"done":false}
                        {"model":"qwen3:1.7b","created_at":"2024-12-11T15:21:23.822542932Z","message":{"role":"assistant","content":""},"done_reason":"stop","done":true,"prompt_eval_count":11,"eval_count":10}"""));

        List<ChatEvent> events = aiServiceWithoutTool.streamingEvents("Hello")
                .collect().asList().await().indefinitely();

        assertEquals(5, events.size());
        assertEquals("Let me ", ((ChatEvent.PartialThinkingEvent) events.get(0)).getText());
        assertEquals("think.", ((ChatEvent.PartialThinkingEvent) events.get(1)).getText());
        assertEquals("Hello", ((ChatEvent.PartialResponseEvent) events.get(2)).getChunk());
        assertEquals("!", ((ChatEvent.PartialResponseEvent) events.get(3)).getChunk());

        ChatEvent.ChatCompletedEvent completed = (ChatEvent.ChatCompletedEvent) events.get(4);
        assertEquals("Hello!", completed.getChatResponse().aiMessage().text());
        assertEquals("Let me think.", completed.getChatResponse().aiMessage().thinking());

        assertThat(mock.requests()).singleElement().satisfies(r -> assertThat(r.path()).isEqualTo("/ollama/api/chat"));
    }

    @Test
    void test_2() throws Exception {
        mock.chat().reply(ChatReply.text("Hello!").chunks(List.of("Hello", "!")));

        var result = aiServiceWithTool.streaming("1", "Hello").collect().asList().await().indefinitely();
        assertEquals(List.of("Hello", "!"), result);

        CapturedRequest request = mock.chat().singleRequest();
        assertThat(request.path()).isEqualTo("/ollama/api/chat");
        assertThat(request.bodyAsJson()).isEqualTo(new ObjectMapper().readTree("""
                {
                  "model" : "llama3.2",
                  "messages" : [ {
                    "role" : "user",
                    "content" : "Hello"
                  }],
                  "tools" : [ %s ],
                  "options" : {
                    "temperature" : 0.8,
                    "top_k" : 40,
                    "top_p" : 0.9
                  },
                  "stream" : true
                }
                """.formatted(SUM_TOOL)));
    }

    @Test
    void test_3() throws Exception {
        mock.chat().replies(List.of(
                ChatReply.toolCall("sum", "{\"firstNumber\":1,\"secondNumber\":1}"),
                ChatReply.text("The result is 2").chunks(List.of("The result is 2"))));

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

        List<CapturedRequest> requests = mock.chat().requests();
        assertThat(requests).hasSize(2);
        ObjectMapper mapper = new ObjectMapper();
        assertThat(requests.get(0).bodyAsJson()).isEqualTo(mapper.readTree("""
                {
                  "model" : "llama3.2",
                  "messages" : [ {
                    "role" : "user",
                    "content" : "1 + 1"
                  }],
                  "tools" : [ %s ],
                  "options" : {
                    "temperature" : 0.8,
                    "top_k" : 40,
                    "top_p" : 0.9
                  },
                  "stream" : true
                }
                """.formatted(SUM_TOOL)));
        assertThat(requests.get(1).bodyAsJson()).isEqualTo(mapper.readTree("""
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
                  "tools" : [ %s ],
                  "options" : {
                    "temperature" : 0.8,
                    "top_k" : 40,
                    "top_p" : 0.9
                  },
                  "stream" : true
                }
                """.formatted(SUM_TOOL)));
    }
}
