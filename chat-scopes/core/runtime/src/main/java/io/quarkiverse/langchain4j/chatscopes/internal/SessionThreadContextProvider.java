package io.quarkiverse.langchain4j.chatscopes.internal;

import java.util.Map;

import org.eclipse.microprofile.context.spi.ThreadContextController;
import org.eclipse.microprofile.context.spi.ThreadContextProvider;
import org.eclipse.microprofile.context.spi.ThreadContextSnapshot;

import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;
import io.quarkus.arc.InjectableContext;
import io.quarkus.arc.InjectableContext.ContextState;
import io.quarkus.arc.ManagedContext;

public class SessionThreadContextProvider implements ThreadContextProvider {
    public static final String THREAD_CONTEXT_TYPE = "SESSION_SCOPE";

    @Override
    public String getThreadContextType() {
        return THREAD_CONTEXT_TYPE;
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
        InjectableContext.ContextState state = container.sessionContext().getStateIfActive();
        if (state == null) {
            return NULL_CONTEXT_SNAPSHOT;
        } else {
            return new ContextSnapshot(state);
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
            ManagedContext sessionContext = container.sessionContext();
            InjectableContext.ContextState toRestore = sessionContext.getStateIfActive();
            // this is executed on another thread, context can but doesn't need to be active
            // here
            if (toRestore != null) {
                // context active, store current state, feed it new one and restore state
                // afterwards
                sessionContext.deactivate();
                return new ThreadContextController() {
                    @Override
                    public void endContext() throws IllegalStateException {
                        if (toRestore.isValid()) {
                            sessionContext.activate(toRestore);
                        }
                    }
                };
            } else {
                // context not active
                return NOOP_CONTROLLER;
            }
        }
    }

    private static final class ContextSnapshot implements ThreadContextSnapshot {

        private final InjectableContext.ContextState state;

        public ContextSnapshot(ContextState state) {
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
            ManagedContext sessionContext = container.sessionContext();
            InjectableContext.ContextState toRestore = sessionContext.getStateIfActive();
            // this is executed on another thread, context can but doesn't need to be active
            // here
            if (toRestore != null) {
                sessionContext.activate(state.isValid() ? state : null);
                if (state.isValid()) {
                    sessionContext.activate(state);
                    return new ThreadContextController() {
                        @Override
                        public void endContext() throws IllegalStateException {
                            sessionContext.deactivate();
                            if (toRestore.isValid()) {
                                sessionContext.activate(toRestore);
                            }
                        }
                    };

                } else {
                    sessionContext.deactivate();
                    return new ThreadContextController() {
                        @Override
                        public void endContext() throws IllegalStateException {
                            if (toRestore.isValid()) {
                                sessionContext.activate(toRestore);
                            }
                        }
                    };
                }
            } else {
                if (state.isValid()) {
                    sessionContext.activate(state);
                    return sessionContext::deactivate;
                } else {
                    return NOOP_CONTROLLER;
                }
            }
        }

    }

}
