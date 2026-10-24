package io.quarkiverse.langchain4j.bedrock.deployment;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;

public class BedrockStreamHelper implements StreamingChatResponseHandler {

    private final AtomicReference<ChatResponse> responseRef = new AtomicReference<>();
    private final AtomicReference<Throwable> errorRef = new AtomicReference<>();
    private final CountDownLatch latch = new CountDownLatch(1);

    public static BedrockStreamHelper create() {
        return new BedrockStreamHelper();
    }

    @Override
    public void onPartialResponse(final String partialResponse) {
    }

    @Override
    public void onCompleteResponse(final ChatResponse completeResponse) {
        responseRef.set(completeResponse);
        latch.countDown();
    }

    @Override
    public void onError(final Throwable error) {
        errorRef.set(error);
    }

    public ChatResponse awaitResponse() throws Throwable {
        latch.await(10, TimeUnit.SECONDS);

        if (errorRef.get() != null) {
            throw errorRef.get();
        }

        return responseRef.get();
    }
}
