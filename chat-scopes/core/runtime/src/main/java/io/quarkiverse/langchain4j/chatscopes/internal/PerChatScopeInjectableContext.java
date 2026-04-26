package io.quarkiverse.langchain4j.chatscopes.internal;

import java.lang.annotation.Annotation;

import io.quarkiverse.langchain4j.chatscopes.PerChatScoped;

public class PerChatScopeInjectableContext extends CustomInjectableContext {
    @Override
    protected CustomContextState state() {
        return ChatScopeManagedContext.currentScope.get();
    }

    @Override
    public Class<? extends Annotation> getScope() {
        return PerChatScoped.class;
    }

    @Override
    public boolean isActive() {
        return state() != null;
    }
}
