package io.quarkiverse.langchain4j.chatscopes.internal;

import java.lang.annotation.Annotation;

import io.quarkiverse.langchain4j.chatscopes.PerChatScoped;

public class PerChatScopeInjectableContext extends BaseChatScopeInjectableContext {
    @Override
    public Class<? extends Annotation> getScope() {
        return PerChatScoped.class;
    }
}
