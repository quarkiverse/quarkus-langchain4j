package io.quarkiverse.langchain4j.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Supplier;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.service.tool.ToolProvider;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.test.toolresolution.MyCustomToolProvider;
import io.quarkiverse.langchain4j.test.toolresolution.TestAiSupplier;
import io.quarkus.test.QuarkusUnitTest;

public class DirectBeanConfigConflictTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(ConflictingService.class, TestAiSupplier.class, MyCustomToolProvider.class,
                            ToolProviderSupplier.class))
            .assertException(error -> assertThat(error)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(
                            "configures both @RegisterAiService.toolProviderSupplier and @RegisterAiService.toolProvider"));

    @RegisterAiService(chatLanguageModelSupplier = TestAiSupplier.class, toolProviderSupplier = ToolProviderSupplier.class, toolProvider = MyCustomToolProvider.class)
    interface ConflictingService {
        String chat(String message);
    }

    public static class ToolProviderSupplier implements Supplier<ToolProvider> {
        @Override
        public ToolProvider get() {
            return null;
        }
    }

    @Test
    void rejectsDuplicateConfiguration() {
        // Asserted while the test application starts.
    }
}
