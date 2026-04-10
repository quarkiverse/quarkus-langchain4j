package io.quarkiverse.langchain4j.test.toolresolution;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Verify startup validation for invalid maxToolCallsPerResponse values.
 */
public class MaxToolCallsPerResponseInvalidValueTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(Tools.class, ModelSupplier.class))
            .assertException(e -> {
                assertThatThrownBy(() -> {
                    throw e;
                })
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("maxToolCallsPerResponse must be 0 or greater");
            });

    @RegisterAiService(maxToolCallsPerResponse = -1, tools = Tools.class, chatLanguageModelSupplier = ModelSupplier.class)
    public interface AiServiceWithInvalidLimit {
        String chat(String message);
    }

    public static class ModelSupplier implements Supplier<ChatModel> {
        @Override
        public ChatModel get() {
            return new ChatModel() {
                @Override
                public ChatResponse chat(ChatRequest chatRequest) {
                    return null;
                }
            };
        }
    }

    @ApplicationScoped
    public static class Tools {
        @Tool
        public String dummy(String message) {
            return "ok";
        }
    }

    @Test
    void should_failStartup_when_invalidValue() {
        // Test assertion is in the unitTest configuration above
    }
}
