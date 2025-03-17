package io.quarkiverse.langchain4j.bedrock.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.CustomMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import io.quarkiverse.langchain4j.bedrock.runtime.config.ChatModelConfig;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockDeltaEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamMetadataEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamResponseHandler;
import software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.MessageStopEvent;
import software.amazon.awssdk.services.bedrockruntime.model.StopReason;

public class BedrockConverseStreamingChatModel implements StreamingChatLanguageModel {

    private final BedrockRuntimeAsyncClient client;
    private final String modelId;
    private final ChatModelConfig config;

    public BedrockConverseStreamingChatModel(final BedrockRuntimeAsyncClient client, final String modelId,
            final ChatModelConfig config) {
        this.client = client;
        this.modelId = modelId;
        this.config = config;
    }

    @Override
    public void generate(String userMessage, StreamingResponseHandler<AiMessage> handler) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new UserMessage(userMessage));
        generate(messages, handler);
    }

    @Override
    public void generate(final List<ChatMessage> messages, final StreamingResponseHandler<AiMessage> handler) {
        chat(messages, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(final String partialResponse) {
                handler.onNext(partialResponse);
            }

            @Override
            public void onCompleteResponse(final ChatResponse completeResponse) {
                handler.onComplete(Response.from(completeResponse.aiMessage()));
            }

            @Override
            public void onError(final Throwable error) {
                handler.onError(error);
            }
        });
    }

    @Override
    public void chat(final ChatRequest chatRequest, final StreamingChatResponseHandler handler) {
        StreamContext context = new StreamContext(handler);

        var responseHandler = ConverseStreamResponseHandler.builder()
                .subscriber(ConverseStreamResponseHandler.Visitor.builder()
                        .onMessageStop(context.setStopReason())
                        .onMetadata(context.updateTokenUsage())
                        .onContentBlockDelta(context.handleChunk())
                        .build())
                .onComplete(context.handleCompletion())
                .onError(new Consumer<Throwable>() {
                    @Override
                    public void accept(final Throwable throwable) {
                        handler.onError(throwable);
                    }
                })
                .build();

        client.converseStream(new Consumer<ConverseStreamRequest.Builder>() {
            @Override
            public void accept(final ConverseStreamRequest.Builder request) {
                request
                        .modelId(modelId)
                        .messages(toBedrockMessages(chatRequest))
                        .inferenceConfig(createInferenceConfig());
            }
        }, responseHandler);
    }

    private Consumer<InferenceConfiguration.Builder> createInferenceConfig() {
        return new Consumer<InferenceConfiguration.Builder>() {
            @Override
            public void accept(final InferenceConfiguration.Builder builder) {

                builder.maxTokens(config.maxTokens());
                if (config.temperature().isPresent()) {
                    builder.temperature((float) config.temperature().getAsDouble());
                }

                if (config.topP().isPresent()) {
                    builder.topP((float) config.topP().getAsDouble());
                }
            }
        };
    }

    private List<Message> toBedrockMessages(final ChatRequest chatRequest) {
        return chatRequest.messages().stream().map(this.messageTransformer()).toList();
    }

    private Function<ChatMessage, Message> messageTransformer() {
        return new Function<ChatMessage, Message>() {
            @Override
            public Message apply(final ChatMessage chatMessage) {
                String msg;
                ConversationRole role;
                if (chatMessage instanceof SystemMessage sm) {
                    msg = sm.text();
                    role = ConversationRole.ASSISTANT;
                } else if (chatMessage instanceof UserMessage um) {
                    msg = um.singleText();
                    role = ConversationRole.USER;
                } else if (chatMessage instanceof AiMessage aim) {
                    msg = aim.text();
                    role = ConversationRole.USER;
                } else if (chatMessage instanceof ToolExecutionResultMessage term) {
                    msg = term.text();
                    role = ConversationRole.ASSISTANT;
                } else if (chatMessage instanceof CustomMessage cm) {
                    msg = cm.text();
                    role = ConversationRole.USER;
                } else {
                    throw new IllegalArgumentException(chatMessage == null ? "null" : chatMessage.getClass().getName());
                }

                return Message.builder().content(ContentBlock.fromText(msg)).role(role).build();
            }
        };
    }

    private class StreamContext {
        private final StringBuilder finalCompletion = new StringBuilder();
        private FinishReason stopReason;
        private TokenUsage tokenUsage = new TokenUsage();
        private final StreamingChatResponseHandler handler;

        public StreamContext(final StreamingChatResponseHandler handler) {
            this.handler = handler;
        }

        public Consumer<MessageStopEvent> setStopReason() {
            return new Consumer<MessageStopEvent>() {
                @Override
                public void accept(final MessageStopEvent messageStopEvent) {
                    stopReason = mapFinishReason(messageStopEvent.stopReason());
                }
            };
        }

        public Consumer<ConverseStreamMetadataEvent> updateTokenUsage() {
            return new Consumer<ConverseStreamMetadataEvent>() {
                @Override
                public void accept(final ConverseStreamMetadataEvent metadataEvent) {
                    final var usage = metadataEvent.usage();
                    tokenUsage = tokenUsage.add(
                            new TokenUsage(usage.inputTokens(), usage.outputTokens(), usage.totalTokens()));
                }
            };
        }

        public Consumer<ContentBlockDeltaEvent> handleChunk() {
            return new Consumer<ContentBlockDeltaEvent>() {
                @Override
                public void accept(final ContentBlockDeltaEvent chunk) {
                    var responseText = chunk.delta().text();
                    finalCompletion.append(responseText);
                    handler.onPartialResponse(responseText);
                }
            };
        }

        public Runnable handleCompletion() {
            return new Runnable() {
                @Override
                public void run() {
                    final var metadata = ChatResponseMetadata.builder().modelName(modelId).tokenUsage(tokenUsage)
                            .finishReason(stopReason)
                            .build();

                    var response = ChatResponse.builder()
                            .aiMessage(new AiMessage(finalCompletion.toString()))
                            .metadata(metadata)
                            .build();

                    handler.onCompleteResponse(response);
                }
            };
        }

        private FinishReason mapFinishReason(final StopReason stopReason) {
            if (stopReason == null) {
                return FinishReason.OTHER;
            }
            switch (stopReason) {
                case END_TURN:
                case STOP_SEQUENCE:
                case GUARDRAIL_INTERVENED:
                    return FinishReason.STOP;
                case TOOL_USE:
                    return FinishReason.TOOL_EXECUTION;
                case MAX_TOKENS:
                    return FinishReason.LENGTH;
                case CONTENT_FILTERED:
                    return FinishReason.CONTENT_FILTER;
                default:
                    return FinishReason.OTHER;
            }
        }
    }
}
