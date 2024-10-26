package io.quarkiverse.langchain4j.llama3;

import static dev.langchain4j.data.message.AiMessage.aiMessage;
import static io.quarkiverse.langchain4j.llama3.MessageMapper.toLlama3Message;
import static io.quarkiverse.langchain4j.llama3.copy.Llama3.selectSampler;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.jboss.logging.Logger;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import io.quarkiverse.langchain4j.llama3.copy.ChatFormat;
import io.quarkiverse.langchain4j.llama3.copy.Llama;
import io.quarkiverse.langchain4j.llama3.copy.Llama3;
import io.quarkiverse.langchain4j.llama3.copy.Sampler;

public class Llama3ChatModel implements ChatLanguageModel {

    private static final Logger log = Logger.getLogger(Llama3ChatModel.class);

    private final Path modelPath;
    private final Llama model;
    private final Float temperature;
    private final Integer maxTokens;
    private final Float topP;
    private final Integer seed;
    private final boolean logRequests;
    private final boolean logResponses;

    public Llama3ChatModel(Builder builder) {
        Llama3ModelRegistry llama3ModelRegistry = Llama3ModelRegistry.getOrCreate(builder.modelCachePath);
        try {
            modelPath = llama3ModelRegistry.downloadModel(builder.modelName, builder.quantization,
                    Optional.ofNullable(builder.authToken), Optional.empty());
            model = llama3ModelRegistry.loadModel(builder.modelName, builder.quantization, builder.maxTokens, true);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        temperature = builder.temperature;
        maxTokens = builder.maxTokens;
        topP = builder.topP;
        seed = builder.seed;
        logRequests = builder.logRequests;
        logResponses = builder.logResponses;
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {

        if (logRequests) {
            log.info("Request: " + messages);
        }

        List<ChatFormat.Message> llama3Messages = new ArrayList<>();
        for (ChatMessage message : messages) {
            llama3Messages.add(toLlama3Message(message));
        }

        Llama3.Options options = new Llama3.Options(
                modelPath,
                "", // unused
                "", // unused
                false,
                temperature,
                topP,
                seed,
                maxTokens,
                false, // stream
                false // echo
        );
        Sampler sampler = selectSampler(model.configuration().vocabularySize, options.temperature(), options.topp(),
                options.seed());
        InferenceResponse inferenceResponse = runInference(model, sampler, options, llama3Messages);

        var response = Response.from(aiMessage(inferenceResponse.text()),
                new TokenUsage(inferenceResponse.promptTokens(), inferenceResponse.responseTokens()));

        if (logResponses) {
            log.info("Response: " + response);
        }

        return response;
    }

    private InferenceResponse runInference(Llama model, Sampler sampler, Llama3.Options options,
            List<ChatFormat.Message> messages) {
        Llama.State state = model.createNewState();
        ChatFormat chatFormat = new ChatFormat(model.tokenizer());

        List<Integer> promptTokens = new ArrayList<>(chatFormat.encodeDialogPrompt(true, messages));

        Set<Integer> stopTokens = chatFormat.getStopTokens();
        List<Integer> responseTokens = Llama.generateTokens(model, state, 0, promptTokens, stopTokens, options.maxTokens(),
                sampler, options.echo(), token -> {
                    if (options.stream()) {
                        if (!model.tokenizer().isSpecialToken(token)) {
                            System.out.print(model.tokenizer().decode(List.of(token)));
                        }
                    }
                });
        if (!responseTokens.isEmpty() && stopTokens.contains(responseTokens.getLast())) {
            responseTokens.removeLast();
        }

        return new InferenceResponse(model.tokenizer().decode(responseTokens), promptTokens.size(), responseTokens.size());
    }

    record InferenceResponse(String text, int promptTokens, int responseTokens) {

    }

    public static Builder builder() {
        return new Builder();
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public static class Builder {

        private Optional<Path> modelCachePath;
        private String modelName = Consts.DEFAULT_CHAT_MODEL_NAME;
        private String quantization = Consts.DEFAULT_CHAT_MODEL_QUANTIZATION;
        private String authToken;
        private Integer maxTokens = 4_000;
        private Float temperature = 0.7f;
        private Float topP = 0.95f;
        private Integer seed = 17;
        private boolean logRequests;
        private boolean logResponses;

        public Builder modelCachePath(Optional<Path> modelCachePath) {
            this.modelCachePath = modelCachePath;
            return this;
        }

        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public Builder quantization(String quantization) {
            this.quantization = quantization;
            return this;
        }

        public Builder authToken(String authToken) {
            this.authToken = authToken;
            return this;
        }

        public Builder temperature(Float temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public Builder topP(Float topP) {
            this.topP = topP;
            return this;
        }

        public Builder seed(Integer seed) {
            this.seed = seed;
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

        public Llama3ChatModel build() {
            return new Llama3ChatModel(this);
        }
    }
}
