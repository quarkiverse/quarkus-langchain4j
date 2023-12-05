package io.quarkiverse.langchain4j.openai.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import jakarta.enterprise.inject.spi.DeploymentException;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.Tool;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.test.QuarkusUnitTest;

public class AiServiceWithToolsAndNoMemoryTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClasses())
            .assertException(t -> {
                assertThat(t)
                        .isInstanceOf(DeploymentException.class)
                        .hasMessageContaining("ChatMemoryProvider");
            });

    @RegisterAiService(tools = CustomTool.class)
    interface Assistant {

        String chat(String input);
    }

    @Singleton
    static class CustomTool {

        @Tool
        void doSomething() {

        }
    }

    @Inject
    Assistant assistant;

    @Test
    void test() {
        fail("Should not be called");
    }
}
