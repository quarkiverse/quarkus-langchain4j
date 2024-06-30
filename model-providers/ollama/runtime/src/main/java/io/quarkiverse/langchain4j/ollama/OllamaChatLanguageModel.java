package io.quarkiverse.langchain4j.ollama;

import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;

import java.time.Duration;
import java.util.List;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.*;
import dev.langchain4j.model.ollama.tool.ExperimentalParallelToolsDelegate;
import dev.langchain4j.model.ollama.tool.ExperimentalSequentialToolsDelegate;
import dev.langchain4j.model.ollama.tool.ExperimentalTools;
import dev.langchain4j.model.ollama.tool.NoToolsDelegate;
import dev.langchain4j.model.output.Response;

public class OllamaChatLanguageModel implements ChatLanguageModel {

    private final QuarkusOllamaClient client;
    private final String model;
    private final String format;
    private final Options options;
    private final ChatLanguageModel delegate;

    private OllamaChatLanguageModel(Builder builder) {
        client = new QuarkusOllamaClient(builder.baseUrl, builder.timeout, builder.logRequests, builder.logResponses);
        model = builder.model;
        format = builder.format;
        options = builder.options;
        delegate = getDelegate(ExperimentalTools.valueOf(builder.experimentalTool));
    }

    private ChatLanguageModel getDelegate(ExperimentalTools toolsEnum) {
        return switch (toolsEnum) {
            case NONE -> new NoToolsDelegate(this.client, this.model, this.options, this.format);
            case SEQUENTIAL -> new ExperimentalSequentialToolsDelegate(this.client, this.model, this.options);
            case PARALLEL -> new ExperimentalParallelToolsDelegate(this.client, this.model, this.options);
        };
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        return delegate.generate(messages);
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
        ensureNotEmpty(messages, "messages");
        return delegate.generate(messages, toolSpecifications);
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, ToolSpecification toolSpecification) {
        return delegate.generate(messages, toolSpecification);
    }

    public static final class Builder {
        private String baseUrl = "http://localhost:11434";
        private Duration timeout = Duration.ofSeconds(10);
        private String model;
        private String format;
        private Options options;

        private boolean logRequests = false;
        private boolean logResponses = false;
        private String experimentalTool;

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

        public Builder experimentalTool(String experimentalTool) {
            this.experimentalTool = experimentalTool;
            return this;
        }

        public OllamaChatLanguageModel build() {
            return new OllamaChatLanguageModel(this);
        }
    }
}
