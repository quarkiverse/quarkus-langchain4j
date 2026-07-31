package io.quarkiverse.langchain4j.tests.scopes.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.langchain4j.chatscopes.ChatScope;
import io.quarkiverse.langchain4j.chatscopes.internal.ChatScopeConversationBridge;
import io.quarkiverse.langchain4j.runtime.conversation.ConversationContext;
import io.quarkiverse.langchain4j.runtime.conversation.ConversationEnded;
import io.quarkiverse.langchain4j.runtime.conversation.ConversationStarted;
import io.quarkus.test.QuarkusUnitTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;

public class ChatScopeConversationBridgeTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(ChatScopeConversationBridge.class, ConversationEventCollector.class));

    @Inject
    ConversationEventCollector collector;

    @BeforeEach
    void reset() {
        collector.clear();
    }

    @Test
    @RunOnVertxContext
    void chatScopeBeginActivatesConversation(UniAsserter asserter) {
        asserter.execute(() -> {
            ChatScope.begin();
            String scopeId = ChatScope.id();

            assertThat(ConversationContext.isActive()).isTrue();
            assertThat(ConversationContext.current()).isEqualTo(scopeId);

            assertThat(collector.started()).hasSize(1);
            assertThat(collector.started().get(0)).isEqualTo(scopeId);

            ChatScope.end();
        });
    }

    @Test
    @RunOnVertxContext
    void chatScopeEndDeactivatesConversation(UniAsserter asserter) {
        asserter.execute(() -> {
            ChatScope.begin();
            collector.clear();

            ChatScope.end();

            assertThat(ConversationContext.isActive()).isFalse();
            assertThat(collector.ended()).hasSize(1);
        });
    }

    @Test
    @RunOnVertxContext
    void pushDoesNotChangeConversationId(UniAsserter asserter) {
        asserter.execute(() -> {
            ChatScope.begin();
            String topId = ChatScope.id();
            collector.clear();

            ChatScope.push();
            String childId = ChatScope.id();

            assertThat(childId).isNotEqualTo(topId);
            // Conversation ID stays the top scope's ID — push/pop don't affect it
            assertThat(ConversationContext.current()).isEqualTo(topId);

            // No conversation events fired during push
            assertThat(collector.started()).isEmpty();
            assertThat(collector.ended()).isEmpty();

            ChatScope.pop();
            ChatScope.end();
        });
    }

    @Test
    @RunOnVertxContext
    void popDoesNotChangeConversationId(UniAsserter asserter) {
        asserter.execute(() -> {
            ChatScope.begin();
            String topId = ChatScope.id();
            ChatScope.push();
            collector.clear();

            ChatScope.pop();

            // Conversation ID unchanged throughout
            assertThat(ConversationContext.current()).isEqualTo(topId);

            // No conversation events fired during pop
            assertThat(collector.started()).isEmpty();
            assertThat(collector.ended()).isEmpty();

            ChatScope.end();
        });
    }

    @Test
    @RunOnVertxContext
    void fullLifecycleEvents(UniAsserter asserter) {
        asserter.execute(() -> {
            ChatScope.begin();
            String topId = ChatScope.id();
            ChatScope.push();
            ChatScope.pop();
            ChatScope.end();

            // Only one start and one end — push/pop don't generate conversation events
            assertThat(collector.started()).containsExactly(topId);
            assertThat(collector.ended()).containsExactly(topId);
        });
    }

    @ApplicationScoped
    public static class ConversationEventCollector {
        private final List<String> started = new ArrayList<>();
        private final List<String> ended = new ArrayList<>();

        public void onStarted(@Observes ConversationStarted event) {
            started.add(event.getConversationId());
        }

        public void onEnded(@Observes ConversationEnded event) {
            ended.add(event.getConversationId());
        }

        public List<String> started() {
            return started;
        }

        public List<String> ended() {
            return ended;
        }

        public void clear() {
            started.clear();
            ended.clear();
        }
    }
}
