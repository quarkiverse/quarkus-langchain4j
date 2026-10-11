package io.quarkiverse.langchain4j.runtime.aiservice;

import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.vertx.core.Context;
import io.vertx.core.Vertx;

/**
 * Dispatches the upstream TokenStream response handler when tool execution may block.
 */
public final class TokenStreamResponseDispatcher {

    private TokenStreamResponseDispatcher() {
    }

    public static boolean dispatch(StreamingChatResponseHandler handler, ChatResponse response, Object methodKey) {
        if (!(methodKey instanceof AiServiceMethodCreateInfo method)
                || !method.isSwitchToWorkerThreadForToolExecution()
                || !Context.isOnEventLoopThread()) {
            return false;
        }

        // Use the current (possibly duplicated) context to retain request-scoped state across tool rounds.
        Vertx.currentContext().executeBlocking(() -> {
            try {
                handler.onCompleteResponse(response);
            } catch (Exception e) {
                handler.onError(e);
            }
            return null;
        }, false);
        return true;
    }
}
