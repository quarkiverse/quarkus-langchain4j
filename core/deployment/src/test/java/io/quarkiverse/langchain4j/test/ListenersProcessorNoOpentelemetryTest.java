package io.quarkiverse.langchain4j.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.langchain4j.runtime.listeners.ChatModelSpanContributor;
import io.quarkiverse.langchain4j.runtime.listeners.SpanChatModelListener;
import io.quarkus.arc.All;
import io.quarkus.test.QuarkusUnitTest;

class ListenersProcessorNoOpentelemetryTest {
    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class));

    @Inject
    @All
    List<SpanChatModelListener> spanChatModelListeners;
    @Inject
    @All
    List<ChatModelSpanContributor> chatModelSpanContributors;

    @Test
    void shouldNotHaveSpanChatModelListenerWhenNoOtel() {
        assertThat(spanChatModelListeners).hasSize(1); // The Tracer is active if its on the classpath
        assertThat(chatModelSpanContributors).hasSize(2); // the prompt and completion ones are always active
    }
}
