package io.quarkiverse.langchain4j.chatscopes.internal;

import java.util.Map;

import org.eclipse.microprofile.context.spi.ThreadContextController;
import org.eclipse.microprofile.context.spi.ThreadContextProvider;
import org.eclipse.microprofile.context.spi.ThreadContextSnapshot;

import io.quarkiverse.langchain4j.chatscopes.InvocationScoped;
import io.quarkiverse.langchain4j.chatscopes.internal.InvocationScopeInjectableContext.InvocationScopeContextState;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;

public class InvocationScopeThreadContextProvider implements ThreadContextProvider {

    @Override
    public String getThreadContextType() {
        return InvocationScoped.THREAD_CONTEXT_TYPE;
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
            return null;
        }
        InvocationScopeContextState state = InvocationScopeInjectableContext.current().get();
        if (state == null) {
            return NULL_CONTEXT_SNAPSHOT;
        }
        return new ContextSnapshot(state);
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
            InvocationScopeContextState currentState = InvocationScopeInjectableContext.current().get();
            if (currentState == null) {
                return NOOP_CONTROLLER;
            }
            return new ThreadContextController() {
                @Override
                public void endContext() throws IllegalStateException {
                    InvocationScopeInjectableContext.current().remove();
                    InvocationScopeInjectableContext.current().set(currentState);
                }
            };
        }
    }

    private static final class ContextSnapshot implements ThreadContextSnapshot {

        private final InvocationScopeContextState state;

        public ContextSnapshot(InvocationScopeContextState state) {
            this.state = state;
        }

        @Override
        public ThreadContextController begin() {
            ArcContainer container = Arc.container();
            if (container == null) {
                return NOOP_CONTROLLER;
            }
            InvocationScopeContextState currentState = InvocationScopeInjectableContext.current().get();
            InvocationScopeInjectableContext.current().set(state);

            return new ThreadContextController() {
                @Override
                public void endContext() throws IllegalStateException {
                    InvocationScopeInjectableContext.current().remove();
                    if (currentState != null) {
                        InvocationScopeInjectableContext.current().set(currentState);
                    }
                }
            };
        }
    }

}
