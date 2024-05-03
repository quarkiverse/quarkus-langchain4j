package io.quarkiverse.langchain4j.ollama;

import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static io.quarkiverse.langchain4j.ollama.MessageMapper.toOllamaMessages;

import java.time.Duration;
import java.util.List;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;

public class OllamaChatLanguageModel implements ChatLanguageModel {

    private final OllamaClient client;
    private final String model;
    private final String format;
    private final Options options;

    private OllamaChatLanguageModel(Builder builder) {
        client = new OllamaClient(builder.baseUrl, builder.timeout, builder.logRequests, builder.logResponses);
        model = builder.model;
        format = builder.format;
        options = builder.options;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        ensureNotEmpty(messages, "messages");

        ChatRequest request = ChatRequest.builder()
                .model(model)
                .messages(toOllamaMessages(messages))
                .options(options)
                .format(format)
                .stream(false)
                .build();

        ChatResponse response = client.chat(request);
        return Response.from(
                AiMessage.from(response.message().content()),
                new TokenUsage(response.promptEvalCount(), response.evalCount()));
    }

    public static final class Builder {
        private String baseUrl = "http://localhost:11434";
        private Duration timeout = Duration.ofSeconds(10);
        private String model;
        private String format;
        private Options options;

        private boolean logRequests = false;
        private boolean logResponses = false;

        private Builder() {
        }

        public Builder baseUrl(String val) {
            baseUrl = val;
            return this;
        }

        public Builder timeout(Duration val) {
            this.timeout = val;
            return this;
        }

        public Builder model(String val) {
            model = val;
            return this;
        }

        public Builder format(String val) {
            format = val;
            return this;
        }

        public Builder options(Options val) {
            options = val;
            return this;
        }

        public Builder logRequests(boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public Builder logResponses(boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public OllamaChatLanguageModel build() {
            return new OllamaChatLanguageModel(this);
        }
    }
}
