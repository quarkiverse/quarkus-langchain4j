package io.quarkiverse.langchain4j.test.guardrails;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import dev.langchain4j.service.TokenStream;

public abstract class TokenStreamExecutor {
    protected String execute(Supplier<TokenStream> aiServiceInvocation) throws InterruptedException {
        var latch = new CountDownLatch(1);
        var values = new ArrayList<String>();

        aiServiceInvocation
                .get()
                .onError(t -> {
                    throw new RuntimeException(t);
                })
                .onPartialResponse(values::add)
                .onCompleteResponse(response -> {
                    values.add(response.aiMessage().text());
                    latch.countDown();
                })
                .start();

        latch.await(10, TimeUnit.SECONDS);

        return String.join(" ", values).strip();
    }
}
