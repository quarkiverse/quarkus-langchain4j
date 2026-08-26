package io.quarkiverse.langchain4j.watsonx.runtime;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import com.ibm.watsonx.ai.chat.ChatHandler;
import com.ibm.watsonx.ai.chat.ChatResponse;
import com.ibm.watsonx.ai.chat.SseEventProcessor;
import com.ibm.watsonx.ai.chat.streaming.ChatSubscriber;

public class QuarkusChatSubscriber extends ChatSubscriber {

    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    public QuarkusChatSubscriber(SseEventProcessor processor, ChatHandler handler) {
        super(processor, handler);
    }

    public void cancel() {
        cancelled.set(true);
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    @Override
    public void onNext(String partialMessage) {
        if (isCancelled())
            return;

        super.onNext(partialMessage);
    }

    @Override
    public CompletableFuture<ChatResponse> onComplete() {
        if (isCancelled())
            return CompletableFuture.completedFuture(null);

        var response = processor.buildResponse();
        handler.onCompleteResponse(response);
        return CompletableFuture.completedFuture(response);
    }

    @Override
    public CompletableFuture<Void> onError(Throwable throwable) {
        if (isCancelled())
            return CompletableFuture.completedFuture(null);

        handler.onError(throwable);
        return CompletableFuture.failedFuture(throwable);
    }
}
