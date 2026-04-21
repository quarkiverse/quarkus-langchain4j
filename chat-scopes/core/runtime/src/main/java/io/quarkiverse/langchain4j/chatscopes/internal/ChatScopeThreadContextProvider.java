package io.quarkiverse.langchain4j.chatscopes.internal;

import java.util.Map;

import org.eclipse.microprofile.context.spi.ThreadContextController;
import org.eclipse.microprofile.context.spi.ThreadContextProvider;
import org.eclipse.microprofile.context.spi.ThreadContextSnapshot;

import io.quarkiverse.langchain4j.chatscopes.ChatScope;
import io.quarkiverse.langchain4j.chatscopes.internal.ChatScopeManagedContext.ChatScopeImpl;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;

public class ChatScopeThreadContextProvider implements ThreadContextProvider {

    @Override
    public String getThreadContextType() {
        return ChatScope.THREAD_CONTEXT_TYPE;
    }

    private static final ThreadContextController NOOP_CONTROLLER = new ThreadContextController() {
        @Override
        public void endContext() throws IllegalStateException {
        }
    };

    private static final ThreadContextSnapshot NULL_CONTEXT_SNAPSHOT = new NullContextSnapshot();

    @Override
    public ThreadContextSnapshot currentContext(Map<String, String> map) {
        ArcContainer container = Arc.container();
        if (container == null) {
            // return null as per docs to state that propagation of this context is not
            // supported
            return null;
        }

        // capture the state, null indicates no active context while capturing snapshot
        ChatScopeImpl currentState = ChatScopeManagedContext.currentScope.get();

        if (currentState == null) {
            return NULL_CONTEXT_SNAPSHOT;
        } else {
            return new ContextSnapshot(currentState);
        }
    }

    @Override
    public ThreadContextSnapshot clearedContext(Map<String, String> map) {
        // note that by cleared we mean that we still activate context if need be, just
        // leave the contents blank
        ArcContainer container = Arc.container();
        if (container == null) {
            // return null as per docs to state that propagation of this context is not
            // supported
            return null;
        }
        return NULL_CONTEXT_SNAPSHOT;
    }

    private static final class NullContextSnapshot implements ThreadContextSnapshot {

        @Override
        public ThreadContextController begin() {
            // can be called later on, we should retrieve the container again
            ArcContainer container = Arc.container();
            if (container == null) {
                // this happens on shutdown, if we blow up here it can break shutdown, and stop
                // resources from being cleaned up, causing tests to fail
                return NOOP_CONTROLLER;
            }
            ChatScopeImpl currentState = ChatScopeManagedContext.currentScope.get();
            if (currentState != null) {
                return new ThreadContextController() {
                    @Override
                    public void endContext() throws IllegalStateException {
                        ChatScopeManagedContext.currentScope.remove();
                        ChatScopeManagedContext.currentScope.set(currentState);
                    }
                };
            } else {
                return NOOP_CONTROLLER;
            }
        }
    }

    private static final class ContextSnapshot implements ThreadContextSnapshot {

        private final ChatScopeImpl state;

        public ContextSnapshot(ChatScopeImpl state) {
            this.state = state;
        }

        @Override
        public ThreadContextController begin() {
            // can be called later on, we should retrieve the container again
            ArcContainer container = Arc.container();
            if (container == null) {
                // this happens on shutdown, if we blow up here it can break shutdown, and stop
                // resources from being cleaned up, causing tests to fail
                return NOOP_CONTROLLER;
            }
            ChatScopeImpl currentState = ChatScopeManagedContext.currentScope.get();

            ChatScopeManagedContext.currentScope.set(state);

            return new ThreadContextController() {
                @Override
                public void endContext() throws IllegalStateException {
                    ChatScopeManagedContext.currentScope.remove();
                    if (currentState != null) {
                        ChatScopeManagedContext.currentScope.set(currentState);
                    }
                }
            };
        }
    }

}
