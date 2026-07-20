package io.quarkiverse.langchain4j.agentic.deployment.validation;

import static org.junit.jupiter.api.Assertions.fail;

import org.assertj.core.api.Assertions;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.declarative.A2AClientAgent;
import dev.langchain4j.agentic.declarative.A2AServerUrlSupplier;
import dev.langchain4j.service.IllegalConfigurationException;
import dev.langchain4j.service.V;
import io.quarkus.test.QuarkusUnitTest;

public class NonStaticA2AServerUrlSupplierTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class).addClasses(A2AAgentWithNonStaticSupplier.class))
            .assertException(
                    throwable -> Assertions.assertThat(throwable).isInstanceOf(IllegalConfigurationException.class)
                            .hasMessageContaining("static"));

    @Test
    public void test() {
        fail("should never be called");
    }

    public interface A2AAgentWithNonStaticSupplier {

        @A2AClientAgent(description = "Generate a story", outputKey = "story")
        String generateStory(@V("topic") String topic);

        @A2AServerUrlSupplier
        default String a2aServerUrl() {
            return "http://localhost:8080";
        }
    }
}
