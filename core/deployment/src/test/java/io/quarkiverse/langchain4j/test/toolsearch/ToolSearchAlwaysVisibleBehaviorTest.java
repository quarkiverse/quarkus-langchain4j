package io.quarkiverse.langchain4j.test.toolsearch;

import static dev.langchain4j.data.message.ChatMessageType.TOOL_EXECUTION_RESULT;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.SearchBehavior;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.test.QuarkusUnitTest;

/**
 * A tool annotated with {@code @Tool(searchBehavior = }{@link SearchBehavior#ALWAYS_VISIBLE}{@code )} must be
 * exposed to the model upfront even when the AI service uses a {@code ToolSearchStrategy}, while the tools left at
 * the default {@link SearchBehavior#SEARCHABLE} stay hidden until the model finds them through the search tool.
 */
public class ToolSearchAlwaysVisibleBehaviorTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(AlwaysVisibleToolSearchModel.class,
                            AlwaysVisibleToolSearchModelSupplier.class,
                            FakeToolSearchStrategy.class,
                            BookingTools.class,
                            AlwaysVisibleTools.class,
                            ServiceWithAlwaysVisibleTool.class));

    @RegisterAiService(tools = { BookingTools.class,
            AlwaysVisibleTools.class }, chatLanguageModelSupplier = AlwaysVisibleToolSearchModelSupplier.class)
    interface ServiceWithAlwaysVisibleTool {

        String chat(@UserMessage String msg, @MemoryId Object id);

    }

    @Inject
    ServiceWithAlwaysVisibleTool service;

    @Test
    @ActivateRequestContext
    void alwaysVisibleToolIsOfferedUpfrontAndIsExecutable() {
        String answer = service.chat("what time is it", 1);
        assertEquals(AlwaysVisibleTools.TOOL_RESULT, answer);
    }

    @Test
    @ActivateRequestContext
    void alwaysVisibleToolSurvivesASearchRoundExactlyOnce() {
        String answer = service.chat("get my booking details", 2);
        assertEquals("REAL_TOOL_RESULT", answer);
    }

    public static class AlwaysVisibleToolSearchModelSupplier implements Supplier<ChatModel> {
        @Override
        public ChatModel get() {
            return new AlwaysVisibleToolSearchModel();
        }
    }

    /**
     * Drives tool search with a mix of a {@code SEARCHABLE} tool and an {@code ALWAYS_VISIBLE} one, and encodes the
     * expectations of every stage in the answer so a misbehaving stage produces a descriptive failure:
     * <ul>
     * <li>first turn: the search tool and the always-visible tool must be offered, the searchable tool must not;</li>
     * <li>after the search tool runs: the found tool must have been added, and the always-visible tool must still be
     * offered exactly once (not dropped and not duplicated);</li>
     * <li>finally: the executed tool's result is returned as the answer.</li>
     * </ul>
     * Which branch is taken is decided by the user message: one mentioning "booking" goes through the search tool, any
     * other one calls the always-visible tool directly.
     */
    public static class AlwaysVisibleToolSearchModel implements ChatModel {

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            List<ChatMessage> messages = chatRequest.messages();
            ChatMessage lastMessage = messages.get(messages.size() - 1);

            if (lastMessage.type().equals(TOOL_EXECUTION_RESULT)) {
                ToolExecutionResultMessage result = (ToolExecutionResultMessage) lastMessage;
                // Tool results are JSON-serialized, so the real tool result text is quoted.
                if (result.text().contains(AlwaysVisibleTools.TOOL_RESULT)) {
                    return answer(AlwaysVisibleTools.TOOL_RESULT);
                }
                if (result.text().contains("REAL_TOOL_RESULT")) {
                    return answer("REAL_TOOL_RESULT");
                }
                // The search tool just ran.
                int alwaysVisibleCount = countTool(chatRequest, AlwaysVisibleTools.TOOL_NAME);
                if (alwaysVisibleCount != 1) {
                    return answer("ALWAYS_VISIBLE_TOOL_OFFERED_" + alwaysVisibleCount + "_TIMES_AFTER_SEARCH");
                }
                if (!hasTool(chatRequest, FakeToolSearchStrategy.FOUND_TOOL_NAME)) {
                    return answer("FOUND_TOOL_NOT_AVAILABLE");
                }
                return callTool(FakeToolSearchStrategy.FOUND_TOOL_NAME);
            }

            // First turn: the search tool and the always-visible tool are exposed, the searchable one is hidden.
            if (!hasTool(chatRequest, FakeToolSearchStrategy.SEARCH_TOOL_NAME)) {
                return answer("NO_SEARCH_TOOL");
            }
            if (!hasTool(chatRequest, AlwaysVisibleTools.TOOL_NAME)) {
                return answer("ALWAYS_VISIBLE_TOOL_HIDDEN");
            }
            if (hasTool(chatRequest, FakeToolSearchStrategy.FOUND_TOOL_NAME)) {
                return answer("SEARCHABLE_TOOL_NOT_NARROWED");
            }
            if (mentionsBooking(messages)) {
                return callTool(FakeToolSearchStrategy.SEARCH_TOOL_NAME);
            }
            return callTool(AlwaysVisibleTools.TOOL_NAME);
        }

        private static boolean mentionsBooking(List<ChatMessage> messages) {
            for (ChatMessage message : messages) {
                if (message instanceof dev.langchain4j.data.message.UserMessage userMessage
                        && userMessage.hasSingleText()
                        && userMessage.singleText().contains("booking")) {
                    return true;
                }
            }
            return false;
        }

        private static boolean hasTool(ChatRequest chatRequest, String name) {
            return countTool(chatRequest, name) > 0;
        }

        private static int countTool(ChatRequest chatRequest, String name) {
            List<ToolSpecification> toolSpecifications = chatRequest.toolSpecifications();
            if (toolSpecifications == null) {
                return 0;
            }
            return (int) toolSpecifications.stream().filter(spec -> spec.name().equals(name)).count();
        }

        private static ChatResponse callTool(String name) {
            ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                    .name(name)
                    .id(name)
                    .build();
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from(toolExecutionRequest))
                    .finishReason(FinishReason.TOOL_EXECUTION)
                    .build();
        }

        private static ChatResponse answer(String text) {
            return ChatResponse.builder().aiMessage(new AiMessage(text)).build();
        }
    }

    @ApplicationScoped
    public static class AlwaysVisibleTools {

        public static final String TOOL_NAME = "currentTime";
        public static final String TOOL_RESULT = "ALWAYS_VISIBLE_TOOL_RESULT";

        @Tool(value = "Returns the current time", searchBehavior = SearchBehavior.ALWAYS_VISIBLE)
        public String currentTime() {
            return TOOL_RESULT;
        }
    }
}
