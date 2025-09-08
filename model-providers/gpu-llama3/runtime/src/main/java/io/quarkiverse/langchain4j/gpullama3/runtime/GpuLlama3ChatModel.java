package io.quarkiverse.langchain4j.gpullama3.runtime;

import java.nio.file.Path;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

public class GpuLlama3ChatModel implements ChatModel {

    private final dev.langchain4j.model.gpullama3.GpuLlama3ChatModel delegate;

    private GpuLlama3ChatModel(dev.langchain4j.model.gpullama3.GpuLlama3ChatModel delegate) {
        this.delegate = delegate;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {
        return delegate.chat(chatRequest);
    }

    public String chat(String userMessage) {
        return delegate.chat(userMessage);
    }

    public static class Builder {
        private Path modelPath;

        public Builder modelPath(Path modelPath) {
            this.modelPath = modelPath;
            return this;
        }

        public GpuLlama3ChatModel build() {
            dev.langchain4j.model.gpullama3.GpuLlama3ChatModel upstream = dev.langchain4j.model.gpullama3.GpuLlama3ChatModel
                    .builder()
                    .modelPath(modelPath)
                    .build();
            return new GpuLlama3ChatModel(upstream);
        }
    }
}
