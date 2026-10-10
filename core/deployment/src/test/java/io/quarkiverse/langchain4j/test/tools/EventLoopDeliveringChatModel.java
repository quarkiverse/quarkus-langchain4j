package io.quarkiverse.langchain4j.test.tools;

import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onCompleteResponse;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onPartialResponse;

import java.util.List;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import io.quarkiverse.langchain4j.test.Lists;
import io.quarkus.arc.Arc;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;

/**
 * Delivers every response on a Vert.x event loop, as the Vert.x based HTTP clients of the model providers do, and
 * routes exceptions thrown by the handler to {@code onError}, as the upstream model implementations do.
 * The first response asks for the first available tool with the user message as argument, the second one streams the
 * tool result back.
 */
public class EventLoopDeliveringChatModel implements StreamingChatModel {

    @Override
    public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        Vertx vertx = Arc.container().instance(Vertx.class).get();
        vertx.runOnContext(new Handler<Void>() {
            @Override
            public void handle(Void ignored) {
                respond(chatRequest, handler);
            }
        });
    }

    private void respond(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        ChatMessage last = Lists.last(chatRequest.messages());
        if (last instanceof ToolExecutionResultMessage toolResult) {
            onPartialResponse(handler, "response: ");
            onPartialResponse(handler, toolResult.text());
            onCompleteResponse(handler, ChatResponse.builder()
                    .aiMessage(new AiMessage(""))
                    .tokenUsage(new TokenUsage(0, 0))
                    .finishReason(FinishReason.STOP)
                    .build());
            return;
        }

        String userText = ((UserMessage) last).singleText();
        onCompleteResponse(handler, ChatResponse.builder()
                .aiMessage(new AiMessage("cannot be blank", List.of(ToolExecutionRequest.builder()
                        .id("my-tool")
                        .name(chatRequest.toolSpecifications().get(0).name())
                        .arguments("{\"m\":\"" + userText + "\"}")
                        .build())))
                .tokenUsage(new TokenUsage(0, 0))
                .finishReason(FinishReason.TOOL_EXECUTION)
                .build());
    }
}
