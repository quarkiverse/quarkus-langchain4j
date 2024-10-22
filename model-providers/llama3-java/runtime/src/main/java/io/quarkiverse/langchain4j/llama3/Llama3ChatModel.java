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

    private final Path modelPath;
    private final Llama model;
    private final Float temperature;
    private final Integer maxTokens;
    private final Float topP;
    private final Integer seed;

    public Llama3ChatModel(Llama3ChatModelBuilder builder) {
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
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {

        List<ChatFormat.Message> llamaMessages = new ArrayList<>();
        for (ChatMessage message : messages) {
            llamaMessages.add(toLlama3Message(message));
        }

        String systemPrompt = llamaMessages.stream().filter(m -> m.role().equals(ChatFormat.Role.SYSTEM)).findFirst().map(
                ChatFormat.Message::content).orElse(null);
        String prompt = llamaMessages.stream().filter(m -> m.role().equals(ChatFormat.Role.USER)).findFirst().map(
                ChatFormat.Message::content).orElse(null);
        if (prompt == null) {
            throw new IllegalArgumentException("No UserMessage found");
        }

        Llama3.Options options = new Llama3.Options(
                modelPath,
                prompt,
                systemPrompt,
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
        InferenceResponse inferenceResponse = runInstructOnce(model, sampler, options);

        return Response.from(aiMessage(inferenceResponse.text()),
                new TokenUsage(inferenceResponse.promptTokens(), inferenceResponse.responseTokens()));
    }

    private InferenceResponse runInstructOnce(Llama model, Sampler sampler, Llama3.Options options) {
        if (options.stream()) {
            throw new IllegalStateException("stream in not supported");
        }

        Llama.State state = model.createNewState();
        ChatFormat chatFormat = new ChatFormat(model.tokenizer());

        List<Integer> promptTokens = new ArrayList<>();
        promptTokens.add(chatFormat.getBeginOfText());
        if (options.systemPrompt() != null) {
            promptTokens
                    .addAll(chatFormat.encodeMessage(new ChatFormat.Message(ChatFormat.Role.SYSTEM, options.systemPrompt())));
        }
        promptTokens.addAll(chatFormat.encodeMessage(new ChatFormat.Message(ChatFormat.Role.USER, options.prompt())));
        promptTokens.addAll(chatFormat.encodeHeader(new ChatFormat.Message(ChatFormat.Role.ASSISTANT, "")));

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

    public static Llama3ChatModelBuilder builder() {
        return new Llama3ChatModelBuilder();
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public static class Llama3ChatModelBuilder {

        private Optional<Path> modelCachePath;
        private String modelName = "mukel/Llama-3.2-3B-Instruct-GGUF";
        private String quantization = "Q4_0";
        private String authToken;
        private Integer maxTokens = 4_000;
        private Float temperature = 0.7f;
        private Float topP = 0.95f;
        private Integer seed = 17;

        public Llama3ChatModelBuilder modelCachePath(Optional<Path> modelCachePath) {
            this.modelCachePath = modelCachePath;
            return this;
        }

        public Llama3ChatModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public Llama3ChatModelBuilder quantization(String quantization) {
            this.quantization = quantization;
            return this;
        }

        public Llama3ChatModelBuilder authToken(String authToken) {
            this.authToken = authToken;
            return this;
        }

        public Llama3ChatModelBuilder temperature(Float temperature) {
            this.temperature = temperature;
            return this;
        }

        public Llama3ChatModelBuilder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public Llama3ChatModelBuilder topP(Float topP) {
            this.topP = topP;
            return this;
        }

        public Llama3ChatModelBuilder seed(Integer seed) {
            this.seed = seed;
            return this;
        }

        public Llama3ChatModel build() {
            return new Llama3ChatModel(this);
        }
    }
}
