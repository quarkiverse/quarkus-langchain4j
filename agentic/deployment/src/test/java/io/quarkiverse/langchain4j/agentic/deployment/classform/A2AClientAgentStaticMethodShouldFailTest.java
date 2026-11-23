package io.quarkiverse.langchain4j.agentic.deployment.classform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.declarative.A2AClientAgent;
import dev.langchain4j.service.IllegalConfigurationException;
import dev.langchain4j.service.V;
import io.quarkus.test.QuarkusExtensionTest;

public class A2AClientAgentStaticMethodShouldFailTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(new Supplier<JavaArchive>() {
                @Override
                public JavaArchive get() {
                    return ShrinkWrap.create(JavaArchive.class).addClass(RemoteWriter.class);
                }
            })
            .assertException(new Consumer<Throwable>() {
                @Override
                public void accept(Throwable throwable) {
                    assertThat(throwable).isInstanceOf(IllegalConfigurationException.class)
                            .hasMessageContaining("The method 'writeStory' of class '" + RemoteWriter.class.getName()
                                    + "' is annotated with @A2AClientAgent, but an A2A client agent must be declared as "
                                    + "an interface");
                }
            });

    @Test
    void test() {
        fail("should never be called");
    }

    public static class RemoteWriter {

        @A2AClientAgent(a2aServerUrl = "http://localhost:8080", description = "Writes a story", outputKey = "story")
        public static String writeStory(@V("topic") String topic) {
            return null;
        }
    }
}
