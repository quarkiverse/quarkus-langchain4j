package io.quarkiverse.langchain4j.agentic.deployment.classform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ToolSearchStrategySupplier;
import dev.langchain4j.service.IllegalConfigurationException;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.tool.search.ToolSearchStrategy;
import dev.langchain4j.service.tool.search.simple.SimpleToolSearchStrategy;
import io.quarkus.test.QuarkusExtensionTest;

public class StaticAndInstanceToolSearchStrategySupplierShouldFailTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(new Supplier<JavaArchive>() {
                @Override
                public JavaArchive get() {
                    return ShrinkWrap.create(JavaArchive.class).addClass(SearchAgent.class);
                }
            })
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "unused")
            .assertException(new Consumer<Throwable>() {
                @Override
                public void accept(Throwable throwable) {
                    assertThat(throwable).isInstanceOf(IllegalConfigurationException.class)
                            .hasMessageContaining(
                                    "declares both the static method 'staticSearchStrategy' and the instance method 'searchStrategy'")
                            .hasMessageContaining("ToolSearchStrategySupplier");
                }
            });

    @Test
    void rejectsConflictingHelpersAtBuildTime() {
        fail("The build should reject conflicting tool search strategy suppliers");
    }

    public static class SearchAgent {
        @Agent
        public native String chat(@UserMessage String message);

        @ToolSearchStrategySupplier
        static ToolSearchStrategy staticSearchStrategy() {
            return SimpleToolSearchStrategy.builder().build();
        }

        @ToolSearchStrategySupplier
        ToolSearchStrategy searchStrategy() {
            return SimpleToolSearchStrategy.builder().build();
        }
    }
}
