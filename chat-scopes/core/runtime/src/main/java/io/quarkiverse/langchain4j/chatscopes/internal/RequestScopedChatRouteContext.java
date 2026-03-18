package io.quarkiverse.langchain4j.chatscopes.internal;

import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Default;

import io.quarkiverse.langchain4j.chatscopes.ChatRouteContext;

@RequestScoped
@Default
public class RequestScopedChatRouteContext implements ChatRouteContext {

    ChatRouteContext delegate;

    public void setDelegate(ChatRouteContext delegate) {
        this.delegate = delegate;
    }

    @Override
    public ChatRouteContext.Request request() {
        return delegate.request();
    }

    @Override
    public ChatRouteContext.ResponseChannel response() {
        return delegate.response();
    }

}
