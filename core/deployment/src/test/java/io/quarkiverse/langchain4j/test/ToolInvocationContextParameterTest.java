package io.quarkiverse.langchain4j.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.test.QuarkusUnitTest;

/**
 * An {@link InvocationContext} tool parameter is supplied by the framework, just like a
 * {@code @ToolMemoryId} parameter or an {@code InvocationParameters} one, so it must not be
 * exposed to the LLM in the tool schema. This mirrors {@code DefaultToolExecutor#prepareArguments}
 * in plain langchain4j.
 */
public class ToolInvocationContextParameterTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(MyAiService.class, ContextTools.class,
                            MyChatModelSupplier.class, MyChatModel.class, Lists.class));

    /** The tool specifications the stub model was handed, i.e. exactly what the LLM would see. */
    static volatile List<ToolSpecification> capturedToolSpecifications;

    @Inject
    MyAiService aiService;

    @Test
    @ActivateRequestContext
    void shouldNotExposeTheInvocationContextParameterToTheLlm() {
        aiService.chat("lookup");

        JsonObjectSchema schema = capturedSchemaOf("lookup");
        assertThat(schema.properties()).containsOnlyKeys("query");
        assertThat(schema.required()).containsExactly("query");
    }

    @Test
    @ActivateRequestContext
    void shouldNotExposeTheInvocationContextParameterWhenItComesFirst() {
        aiService.chat("lookupContextFirst");

        JsonObjectSchema schema = capturedSchemaOf("lookupContextFirst");
        assertThat(schema.properties()).containsOnlyKeys("query");
        assertThat(schema.required()).containsExactly("query");
    }

    @Test
    @ActivateRequestContext
    void shouldExposeNoParametersWhenTheContextIsTheOnlyOne() {
        aiService.chat("contextOnly");

        JsonObjectSchema schema = capturedSchemaOf("contextOnly");
        assertThat(schema.properties()).isEmpty();
        assertThat(schema.required()).isEmpty();
    }

    @Test
    @ActivateRequestContext
    void shouldInjectTheInvocationContextIntoTheToolParameter() {
        assertThat(aiService.chat("lookup"))
                .isEqualTo("response: weather:" + MyAiService.class.getName() + "#chat");
    }

    @Test
    @ActivateRequestContext
    void shouldInjectTheInvocationContextWhenItComesFirst() {
        assertThat(aiService.chat("lookupContextFirst"))
                .isEqualTo("response: chat:weather");
    }

    @Test
    @ActivateRequestContext
    void shouldInjectTheInvocationContextWhenItIsTheOnlyParameter() {
        assertThat(aiService.chat("contextOnly"))
                .isEqualTo("response: " + MyAiService.class.getName());
    }

    private static JsonObjectSchema capturedSchemaOf(String toolName) {
        assertThat(capturedToolSpecifications)
                .as("the stub model should have been handed the tool specifications")
                .isNotNull();
        return capturedToolSpecifications.stream()
                .filter(spec -> spec.name().equals(toolName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No tool specification named " + toolName))
                .parameters();
    }

    @RegisterAiService(chatLanguageModelSupplier = MyChatModelSupplier.class, tools = ContextTools.class)
    public interface MyAiService {
        String chat(@UserMessage String toolName);
    }

    @ApplicationScoped
    public static class ContextTools {

        @Tool("Looks up a thing")
        String lookup(String query, InvocationContext context) {
            return query + ":" + context.interfaceName() + "#" + context.methodName();
        }

        @Tool("Looks up a thing, with the context first")
        String lookupContextFirst(InvocationContext context, String query) {
            return context.methodName() + ":" + query;
        }

        @Tool("Needs nothing but the context")
        String contextOnly(InvocationContext context) {
            return context.interfaceName();
        }
    }

    public static class MyChatModelSupplier implements Supplier<ChatModel> {
        @Override
        public ChatModel get() {
            return new MyChatModel();
        }
    }

    /**
     * Asks for the tool named by the user message on the first turn, then echoes the tool result.
     */
    public static class MyChatModel implements ChatModel {
        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            capturedToolSpecifications = chatRequest.toolSpecifications();

            List<ChatMessage> messages = chatRequest.messages();
            if (messages.size() == 1) {
                String toolName = ((dev.langchain4j.data.message.UserMessage) messages.get(0)).singleText();
                String arguments = "contextOnly".equals(toolName) ? "{}" : "{\"query\": \"weather\"}";
                return ChatResponse.builder()
                        .aiMessage(new AiMessage("", List.of(
                                ToolExecutionRequest.builder()
                                        .id("tool-" + toolName)
                                        .name(toolName)
                                        .arguments(arguments)
                                        .build())))
                        .tokenUsage(new TokenUsage(0, 0))
                        .finishReason(FinishReason.TOOL_EXECUTION)
                        .build();
            } else if (messages.size() == 3) {
                ToolExecutionResultMessage toolResult = (ToolExecutionResultMessage) Lists.last(messages);
                return ChatResponse.builder()
                        .aiMessage(AiMessage.from("response: " + toolResult.text()))
                        .finishReason(FinishReason.STOP)
                        .build();
            }
            throw new RuntimeException("Unexpected number of messages: " + messages.size());
        }
    }
}
