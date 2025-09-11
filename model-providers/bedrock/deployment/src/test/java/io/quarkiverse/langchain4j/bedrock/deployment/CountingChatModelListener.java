package io.quarkiverse.langchain4j.bedrock.deployment;

import java.util.concurrent.atomic.AtomicInteger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;

import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;

@ApplicationScoped
@Named("CountingChatModelListener")
public class CountingChatModelListener implements ChatModelListener {

    private final AtomicInteger onRequest = new AtomicInteger();
    private final AtomicInteger onResponse = new AtomicInteger();
    private final AtomicInteger onError = new AtomicInteger();

    public void reset() {
        onRequest.set(0);
        onResponse.set(0);
        onError.set(0);
    }

    public int onRequestCount() {
        return onRequest.get();
    }

    public int onResponseCount() {
        return onResponse.get();
    }

    public int onErrorCount() {
        return onError.get();
    }

    @Override
    public void onRequest(ChatModelRequestContext context) {
        onRequest.incrementAndGet();
    }

    @Override
    public void onResponse(ChatModelResponseContext context) {
        onResponse.incrementAndGet();
    }

    @Override
    public void onError(ChatModelErrorContext context) {
        onError.incrementAndGet();
    }
}
