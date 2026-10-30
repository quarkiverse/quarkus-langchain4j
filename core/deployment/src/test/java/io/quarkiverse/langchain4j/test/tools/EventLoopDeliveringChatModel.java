package io.quarkiverse.langchain4j.test.tools;

import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onCompleteResponse;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onPartialResponse;

import java.util.ArrayList;
import java.util.List;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
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
import io.quarkus.arc.Arc;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;

/**
 * Delivers every response on a Vert.x event loop, as the Vert.x based HTTP clients of the model providers do, and
 * routes exceptions thrown by the handler to {@code onError}, as the upstream model implementations do.
 * The first two responses ask for every available tool with the user message as argument, the third one streams the
 * tool results back, followed by the name of the thread that sent the last request, which is the thread the second
 * response requesting tools was handed to.
 */
public class EventLoopDeliveringChatModel implements StreamingChatModel {

    private static final int TOOL_ROUNDS = 2;

    @Override
    public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        String requestingThread = Thread.currentThread().getName();
        Vertx vertx = Arc.container().instance(Vertx.class).get();
        vertx.runOnContext(new Handler<Void>() {
            @Override
            public void handle(Void ignored) {
                respond(chatRequest, handler, requestingThread);
            }
        });
    }

    private void respond(ChatRequest chatRequest, StreamingChatResponseHandler handler, String requestingThread) {
        String userText = null;
        int toolRounds = 0;
        List<String> toolResults = new ArrayList<>();
        for (ChatMessage message : chatRequest.messages()) {
            if (message instanceof UserMessage userMessage) {
                userText = userMessage.singleText();
            } else if (message instanceof AiMessage aiMessage && aiMessage.hasToolExecutionRequests()) {
                toolRounds++;
            } else if (message instanceof ToolExecutionResultMessage toolResult) {
                toolResults.add(toolResult.text());
            }
        }

        if (toolRounds == TOOL_ROUNDS) {
            onPartialResponse(handler, "response: ");
            onPartialResponse(handler, String.join(", ", toolResults));
            onPartialResponse(handler, ", requested from " + requestingThread);
            onCompleteResponse(handler, ChatResponse.builder()
                    .aiMessage(new AiMessage(""))
                    .tokenUsage(new TokenUsage(0, 0))
                    .finishReason(FinishReason.STOP)
                    .build());
            return;
        }

        List<ToolExecutionRequest> toolExecutionRequests = new ArrayList<>();
        for (ToolSpecification toolSpecification : chatRequest.toolSpecifications()) {
            toolExecutionRequests.add(ToolExecutionRequest.builder()
                    .id("my-tool-" + toolRounds + "-" + toolExecutionRequests.size())
                    .name(toolSpecification.name())
                    .arguments("{\"m\":\"" + userText + "\"}")
                    .build());
        }
        onCompleteResponse(handler, ChatResponse.builder()
                .aiMessage(new AiMessage("cannot be blank", toolExecutionRequests))
                .tokenUsage(new TokenUsage(0, 0))
                .finishReason(FinishReason.TOOL_EXECUTION)
                .build());
    }
}
