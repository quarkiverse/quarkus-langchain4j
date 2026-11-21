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

public class InjectConstructorOnAiServiceClassShouldFailTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClasses(Assistant.class, Greeting.class))
            .assertException(throwable -> Assertions.assertThat(throwable)
                    .isInstanceOf(IllegalConfigurationException.class)
                    .hasMessageContaining("declares a constructor annotated with @Inject"));

    @Test
    public void test() {
        fail("should never be called");
    }

    @jakarta.enterprise.context.ApplicationScoped
    public static class Greeting {
    }

    @RegisterAiService
    public static class Assistant {

        final Greeting greeting;

        public Assistant() {
            this(null);
        }

        @jakarta.inject.Inject
        public Assistant(Greeting greeting) {
            this.greeting = greeting;
        }

        public native String chat(@UserMessage String message);
    }
}
