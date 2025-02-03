package io.quarkiverse.langchain4j.test.listeners;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.maven.dependency.Dependency;
import io.quarkus.test.QuarkusUnitTest;

class ListenersProcessorOnlySpanChatModelListenerTest
        extends ListenersProcessorAbstractSpanChatModelListenerTest {
    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(
                    ListenersProcessorAbstractSpanChatModelListenerTest::appWithInMemorySpanExporter)
            .setForcedDependencies(
                    List.of(Dependency.of("io.quarkus", "quarkus-opentelemetry", "3.15.2")));

    @Test
    void shouldHaveSpanChatModelListenerWithoutContributors() {
        assertThat(spanChatModelListener).isNotNull();
        assertThat(contributors).isEmpty();
    }
}
