package io.quarkiverse.langchain4j.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Supplier;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.test.QuarkusUnitTest;

public class MemoryConfigInvalidWindowTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(ConfiguredService.class, ModelSupplier.class))
            .assertException(error -> assertThat(error)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("@RegisterAiService.chatMemoryMaxMessages must be greater than zero"));

    @RegisterAiService(chatLanguageModelSupplier = ModelSupplier.class, chatMemoryMaxMessages = 0)
    interface ConfiguredService {
        String chat(String message);
    }

    public static class ModelSupplier implements Supplier<ChatModel> {
        @Override
        public ChatModel get() {
            return null;
        }
    }

    @Test
    void rejectsNonPositiveWindowSize() {
        // Asserted while the test application starts.
    }
}
