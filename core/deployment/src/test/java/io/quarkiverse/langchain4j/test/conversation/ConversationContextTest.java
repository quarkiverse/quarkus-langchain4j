package io.quarkiverse.langchain4j.test.conversation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.langchain4j.runtime.conversation.ConversationContext;
import io.quarkiverse.langchain4j.runtime.conversation.ConversationEnded;
import io.quarkiverse.langchain4j.runtime.conversation.ConversationStarted;
import io.quarkus.test.QuarkusUnitTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;

public class ConversationContextTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(ConversationEventCollector.class));

    @Inject
    ConversationEventCollector collector;

    @AfterEach
    void cleanup() {
        collector.clear();
    }

    @Test
    @RunOnVertxContext
    void beginSetsConversationId(UniAsserter asserter) {
        asserter.execute(() -> {
            ConversationContext.begin("conv-123");
            assertThat(ConversationContext.current()).isEqualTo("conv-123");
            assertThat(ConversationContext.isActive()).isTrue();
            ConversationContext.end();
        });
    }

    @Test
    @RunOnVertxContext
    void endClearsConversationId(UniAsserter asserter) {
        asserter.execute(() -> {
            ConversationContext.begin("conv-456");
            ConversationContext.end();
            assertThat(ConversationContext.current()).isNull();
            assertThat(ConversationContext.isActive()).isFalse();
        });
    }

    @Test
    @RunOnVertxContext
    void endWhenNotActiveIsNoOp(UniAsserter asserter) {
        asserter.execute(() -> {
            ConversationContext.end();
            assertThat(collector.events()).isEmpty();
        });
    }

    @Test
    @RunOnVertxContext(runOnEventLoop = false)
    void beginAutoEndsPreviousConversation(UniAsserter asserter) {
        asserter.execute(() -> {
            ConversationContext.begin("first");
            assertThat(ConversationContext.current()).isEqualTo("first");
            assertThat(ConversationContext.isActive()).isTrue();
            collector.clear();

            ConversationContext.begin("second");
            assertThat(ConversationContext.current()).isEqualTo("second");

            // The previous conversation was auto-ended before the new one started
            assertThat(collector.events()).hasSize(2);
            assertThat(collector.events().get(0)).isInstanceOf(ConversationEnded.class);
            assertThat(((ConversationEnded) collector.events().get(0)).getConversationId()).isEqualTo("first");
            assertThat(collector.events().get(1)).isInstanceOf(ConversationStarted.class);
            assertThat(((ConversationStarted) collector.events().get(1)).getConversationId()).isEqualTo("second");

            ConversationContext.end();
        });
    }

    @Test
    @RunOnVertxContext
    void firesConversationStartedEvent(UniAsserter asserter) {
        asserter.execute(() -> {
            ConversationContext.begin("evt-start");
            assertThat(collector.events()).hasSize(1);
            assertThat(collector.events().get(0)).isInstanceOf(ConversationStarted.class);
            assertThat(((ConversationStarted) collector.events().get(0)).getConversationId()).isEqualTo("evt-start");
            ConversationContext.end();
        });
    }

    @Test
    @RunOnVertxContext
    void firesConversationEndedEvent(UniAsserter asserter) {
        asserter.execute(() -> {
            ConversationContext.begin("evt-end");
            collector.clear();
            ConversationContext.end();
            assertThat(collector.events()).hasSize(1);
            assertThat(collector.events().get(0)).isInstanceOf(ConversationEnded.class);
            assertThat(((ConversationEnded) collector.events().get(0)).getConversationId()).isEqualTo("evt-end");
        });
    }

    @ApplicationScoped
    public static class ConversationEventCollector {
        private final List<Object> events = new ArrayList<>();

        public void onStarted(@Observes ConversationStarted event) {
            events.add(event);
        }

        public void onEnded(@Observes ConversationEnded event) {
            events.add(event);
        }

        public List<Object> events() {
            return events;
        }

        public void clear() {
            events.clear();
        }
    }
}
