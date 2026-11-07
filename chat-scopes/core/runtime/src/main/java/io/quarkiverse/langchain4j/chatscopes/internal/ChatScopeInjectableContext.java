package io.quarkiverse.langchain4j.chatscopes.internal;

import java.lang.annotation.Annotation;

import jakarta.enterprise.context.spi.Contextual;

import io.quarkiverse.langchain4j.chatscopes.ChatScoped;
import io.quarkiverse.langchain4j.chatscopes.internal.ChatScopeManagedContext.ChatScopeImpl;
import io.quarkus.arc.ContextInstanceHandle;

public class ChatScopeInjectableContext extends CustomInjectableContext {
    protected <T> ContextInstanceHandle<T> getInstanceHandle(Contextual<T> contextual, CustomContextState contextState) {
        return ((ChatScopeImpl) contextState).get(contextual, true);
    }

    @Override
    protected CustomContextState state() {
        return ChatScopeManagedContext.currentScope.get();
    }

    @Override
    public Class<? extends Annotation> getScope() {
        return ChatScoped.class;
    }

    @Override
    public boolean isActive() {
        return state() != null;
    }
}
