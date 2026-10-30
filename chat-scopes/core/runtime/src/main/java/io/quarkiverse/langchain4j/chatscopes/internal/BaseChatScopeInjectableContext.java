package io.quarkiverse.langchain4j.chatscopes.internal;

import jakarta.enterprise.context.spi.CreationalContext;

import io.quarkus.arc.InjectableBean;

public abstract class BaseChatScopeInjectableContext extends CustomInjectableContext {
    @Override
    protected ChatScopeManagedContext.ChatScopeImpl state() {
        return ChatScopeManagedContext.currentScope.get();
    }

    @Override
    public boolean isActive() {
        return state() != null;
    }

    @Override
    public void destroy() {
        ChatScopeStoreManager.destroy(state());
        super.destroy();
    }

    @Override
    protected <T> T createInstance(InjectableBean<T> contextual, CreationalContext<T> creationalContext) {
        T createdInstance = contextual.create(creationalContext);
        //printDependents(creationalContext);
        T activatedInstance = (T) ChatScopeStoreManager.activateBean(state(), contextual.getIdentifier(), createdInstance);
        return activatedInstance != null ? activatedInstance : createdInstance;
    }
}
