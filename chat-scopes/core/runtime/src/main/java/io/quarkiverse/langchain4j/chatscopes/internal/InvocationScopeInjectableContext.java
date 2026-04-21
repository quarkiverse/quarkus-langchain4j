package io.quarkiverse.langchain4j.chatscopes.internal;

// ChatScopeInjectableContext
import java.lang.annotation.Annotation;
import java.util.UUID;

import io.quarkiverse.langchain4j.chatscopes.InvocationScoped;
import io.quarkus.arc.Arc;
import io.quarkus.arc.CurrentContext;
import io.quarkus.arc.impl.LazyValue;

/**
 * InvocationScoped means that if there is no invocation scope, start one up
 * when you call a bean method on it. Destroy the scope when the method returns.
 *
 * How does it work?
 * The injectable context will always say that invocation scope is active. When
 * the Arc runtime asks for ContextState, something will always be returned.
 *
 * If the currentContext ThreadLocal is not set, then we create a ContextState
 * and set this threadlocal.
 * Also, activatable ThreadLocal is set to true.
 * Each @InvocationScoped bean will have the {@link InvocationScopeInterceptor}
 * bound to it.
 * This interceptor will check to see if the activatable ThreadLocal is set to
 * true. If it is, then
 * clears this ThreadLocal andit will destroy the context and end the scope when
 * the method it intercepts completes
 *
 */
public class InvocationScopeInjectableContext extends CustomInjectableContext {
    private static LazyValue<CurrentContext<InvocationScopeContextState>> current = new LazyValue<>(
            () -> Arc.container().getCurrentContextFactory()
                    .create(InvocationScoped.class));

    static CurrentContext<InvocationScopeContextState> current() {
        return current.get();
    }

    public static boolean shouldTerminate() {
        if (activatable.get() == null) {
            return false;
        }
        activatable.remove();
        return true;
    }

    public static void terminate() {
        CustomContextState state = current().get();
        if (state != null) {
            state.destroy();
        }
        current().remove();
    }

    public static final ThreadLocal<Boolean> activatable = new ThreadLocal<>();

    @Override
    public Class<? extends Annotation> getScope() {
        return InvocationScoped.class;
    }

    @Override
    public boolean isActive() {
        return true;
    }

    @Override
    protected CustomContextState state() {
        if (current().get() == null) {
            current().set(new InvocationScopeContextState());
            activatable.set(true);
        }
        return current().get();
    }

    public static class InvocationScopeContextState extends CustomContextState {
        String id = UUID.randomUUID().toString();

        public String id() {
            return id;
        }
    }
}
