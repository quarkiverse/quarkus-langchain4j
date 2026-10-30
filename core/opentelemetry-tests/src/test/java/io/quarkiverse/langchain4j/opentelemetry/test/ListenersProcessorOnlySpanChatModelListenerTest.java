package io.quarkiverse.langchain4j.opentelemetry.test;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;

class ListenersProcessorOnlySpanChatModelListenerTest
        extends ListenersProcessorAbstractSpanChatModelListenerTest {
    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(
                    ListenersProcessorAbstractSpanChatModelListenerTest::appWithInMemorySpanExporter);

    @Test
    void shouldHaveSpanChatModelListenerWithoutContributors() {
        assertThat(spanChatModelListener).isNotNull();
        assertThat(contributors).hasSize(2);
    }
}
