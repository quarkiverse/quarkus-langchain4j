package io.quarkiverse.langchain4j.test;

import static org.assertj.core.api.Assertions.assertThat;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.test.toolresolution.TestAiSupplier;
import io.quarkus.test.QuarkusUnitTest;

public class DirectBeanConfigMissingBeanTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(ConfiguredService.class, NotABean.class, TestAiSupplier.class))
            .assertException(error -> assertThat(error)
                    .hasStackTraceContaining("Unsatisfied dependency")
                    .hasStackTraceContaining(NotABean.class.getName()));

    @RegisterAiService(chatLanguageModelSupplier = TestAiSupplier.class, chatMemoryProvider = NotABean.class)
    interface ConfiguredService {
        String chat(String message);
    }

    public static class NotABean implements ChatMemoryProvider {
        @Override
        public ChatMemory get(Object memoryId) {
            return null;
        }
    }

    @Test
    void rejectsAClassThatIsNotACdiBean() {
        // Asserted while the test application starts.
    }
}
