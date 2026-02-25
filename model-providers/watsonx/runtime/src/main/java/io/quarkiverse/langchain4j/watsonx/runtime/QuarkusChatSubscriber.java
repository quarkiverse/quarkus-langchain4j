package io.quarkiverse.langchain4j.watsonx.runtime;

import java.util.concurrent.CompletableFuture;

import com.ibm.watsonx.ai.chat.ChatHandler;
import com.ibm.watsonx.ai.chat.ChatResponse;
import com.ibm.watsonx.ai.chat.SseEventProcessor;
import com.ibm.watsonx.ai.chat.streaming.ChatSubscriber;

public class QuarkusChatSubscriber extends ChatSubscriber {

    public QuarkusChatSubscriber(SseEventProcessor processor, ChatHandler handler) {
        super(processor, handler);
    }

    @Override
    public CompletableFuture<ChatResponse> onComplete() {
        var response = processor.buildResponse();
        handler.onCompleteResponse(response);
        return CompletableFuture.completedFuture(response);
    }

    @Override
    public CompletableFuture<Void> onError(Throwable throwable) {
        handler.onError(throwable);
        return CompletableFuture.failedFuture(throwable);
    }
}
