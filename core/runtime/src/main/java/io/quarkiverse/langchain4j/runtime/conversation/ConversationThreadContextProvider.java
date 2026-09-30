package io.quarkiverse.langchain4j.runtime.conversation;

import java.util.Map;

import org.eclipse.microprofile.context.spi.ThreadContextController;
import org.eclipse.microprofile.context.spi.ThreadContextProvider;
import org.eclipse.microprofile.context.spi.ThreadContextSnapshot;

import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;

/**
 * Propagates the current conversation ID across thread boundaries via MicroProfile Context Propagation.
 */
public class ConversationThreadContextProvider implements ThreadContextProvider {

    public static final String THREAD_CONTEXT_TYPE = "CONVERSATION";

    private static final ThreadContextController NOOP_CONTROLLER = () -> {
    };

    private static final ThreadContextSnapshot NULL_CONTEXT_SNAPSHOT = new NullContextSnapshot();

    @Override
    public String getThreadContextType() {
        return THREAD_CONTEXT_TYPE;
    }

    @Override
    public ThreadContextSnapshot currentContext(Map<String, String> map) {
        ArcContainer container = Arc.container();
        if (container == null) {
            return null;
        }
        String currentId = ConversationContext.current();
        if (currentId == null) {
            return NULL_CONTEXT_SNAPSHOT;
        }
        return new ContextSnapshot(currentId);
    }

    @Override
    public ThreadContextSnapshot clearedContext(Map<String, String> map) {
        ArcContainer container = Arc.container();
        if (container == null) {
            return null;
        }
        return NULL_CONTEXT_SNAPSHOT;
    }

    private static final class NullContextSnapshot implements ThreadContextSnapshot {

        @Override
        public ThreadContextController begin() {
            ArcContainer container = Arc.container();
            if (container == null) {
                return NOOP_CONTROLLER;
            }
            String previous = ConversationContext.current();
            ConversationContext.clear();
            if (previous != null) {
                return () -> ConversationContext.set(previous);
            }
            return NOOP_CONTROLLER;
        }
    }

    private static final class ContextSnapshot implements ThreadContextSnapshot {

        private final String conversationId;

        ContextSnapshot(String conversationId) {
            this.conversationId = conversationId;
        }

        @Override
        public ThreadContextController begin() {
            ArcContainer container = Arc.container();
            if (container == null) {
                return NOOP_CONTROLLER;
            }
            String previous = ConversationContext.current();
            ConversationContext.set(conversationId);
            return () -> {
                ConversationContext.clear();
                if (previous != null) {
                    ConversationContext.set(previous);
                }
            };
        }
    }
}
