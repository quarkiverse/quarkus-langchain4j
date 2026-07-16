package io.quarkiverse.langchain4j.test.toolresolution;

import static org.assertj.core.api.Assertions.assertThat;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.test.QuarkusUnitTest;

public class ToolConfigInvalidLimitTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(ConfiguredService.class, TestAiSupplier.class))
            .assertException(error -> assertThat(error)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("maxToolCallsPerResponse must be 0 or greater"));

    @RegisterAiService(chatLanguageModelSupplier = TestAiSupplier.class, maxToolCallsPerResponse = -1)
    interface ConfiguredService {
        String chat(String message);
    }

    @Test
    void rejectsNegativeLimit() {
        // Asserted while the test application starts.
    }
}
