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

public class NoNativeMethodAiServiceClassShouldFailTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClasses(Assistant.class))
            .assertException(throwable -> Assertions.assertThat(throwable)
                    .isInstanceOf(IllegalConfigurationException.class)
                    .hasMessageContaining("declares no 'native' method"));

    @Test
    public void test() {
        fail("should never be called");
    }

    @RegisterAiService
    public static class Assistant {
        public String chat(@UserMessage String message) {
            return message;
        }
    }
}
