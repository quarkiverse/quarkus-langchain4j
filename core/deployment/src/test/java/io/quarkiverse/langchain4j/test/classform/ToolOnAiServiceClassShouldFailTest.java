package io.quarkiverse.langchain4j.test.classform;

import static org.junit.jupiter.api.Assertions.fail;

import org.assertj.core.api.Assertions;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.service.IllegalConfigurationException;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.test.QuarkusExtensionTest;

public class ToolOnAiServiceClassShouldFailTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClasses(Assistant.class))
            .assertException(throwable -> Assertions.assertThat(throwable)
                    .isInstanceOf(IllegalConfigurationException.class)
                    .hasMessageContaining("tools declared on an AI service class are not supported yet"));

    @Test
    public void test() {
        fail("should never be called");
    }

    @RegisterAiService
    public static class Assistant {
        public native String chat(@UserMessage String message);

        @dev.langchain4j.agent.tool.Tool("Length of a text")
        public int length(String text) {
            return text.length();
        }
    }
}
