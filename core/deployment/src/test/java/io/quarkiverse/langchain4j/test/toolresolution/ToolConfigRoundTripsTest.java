package io.quarkiverse.langchain4j.test.toolresolution;

import static org.assertj.core.api.Assertions.assertThat;

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
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.test.QuarkusUnitTest;

public class ToolConfigRoundTripsTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class));

    @RegisterAiService(chatLanguageModelSupplier = ModelSupplier.class, tools = Tools.class, maxToolCallingRoundTrips = 3)
    interface AiService {
        String chat(String message);
    }

    public static class ModelSupplier implements Supplier<ChatModel> {
        @Override
        public ChatModel get() {
            return new ChatModel() {
                @Override
                public ChatResponse doChat(ChatRequest request) {
                    return ChatResponse.builder()
                            .aiMessage(AiMessage.from(ToolExecutionRequest.builder()
                                    .name("dummy")
                                    .id("dummy")
                                    .arguments("{}")
                                    .build()))
                            .finishReason(FinishReason.TOOL_EXECUTION)
                            .build();
                }
            };
        }
    }

    @ApplicationScoped
    public static class Tools {
        static volatile int invocations;

        @Tool
        public String dummy() {
            invocations++;
            return "ok";
        }
    }

    @Inject
    AiService aiService;

    @Test
    @ActivateRequestContext
    void limitsToolCallingRoundTrips() {
        Tools.invocations = 0;
        try {
            aiService.chat("hello");
        } catch (RuntimeException expected) {
            // The model deliberately requests another tool call forever.
        }
        assertThat(Tools.invocations).isEqualTo(3);
    }
}
