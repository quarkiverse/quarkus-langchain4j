package io.quarkiverse.langchain4j.vertexai.runtime.gemini;

import static io.quarkiverse.langchain4j.runtime.OptionalUtil.firstOrDefault;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Proxy.Type;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.util.TypeLiteral;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.DisabledChatModel;
import dev.langchain4j.model.chat.DisabledStreamingChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.embedding.DisabledEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.quarkiverse.langchain4j.ModelBuilderCustomizer;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkiverse.langchain4j.vertexai.runtime.gemini.config.LangChain4jVertexAiGeminiConfig;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import io.smallrye.config.ConfigValidationException;

@Recorder
public class VertexAiGeminiRecorder {

    private static final String DUMMY_KEY = "dummy";
    private static final TypeLiteral<Instance<ChatModelListener>> CHAT_MODEL_LISTENER_TYPE_LITERAL = new TypeLiteral<>() {
    };
    private static final TypeLiteral<Instance<ModelBuilderCustomizer<VertexAiGeminiChatLanguageModel.Builder>>> CHAT_MODEL_CUSTOMIZER_TYPE_LITERAL = new TypeLiteral<>() {
    };
    private static final TypeLiteral<Instance<ModelBuilderCustomizer<VertexAiGeminiStreamingChatLanguageModel.Builder>>> STREAMING_CHAT_MODEL_CUSTOMIZER_TYPE_LITERAL = new TypeLiteral<>() {
    };
    private static final TypeLiteral<Instance<ModelBuilderCustomizer<VertexAiGeminiEmbeddingModel.Builder>>> EMBEDDING_MODEL_CUSTOMIZER_TYPE_LITERAL = new TypeLiteral<>() {
    };

    private final RuntimeValue<LangChain4jVertexAiGeminiConfig> runtimeConfig;

    public VertexAiGeminiRecorder(RuntimeValue<LangChain4jVertexAiGeminiConfig> runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
    }

    public Function<SyntheticCreationalContext<EmbeddingModel>, EmbeddingModel> embeddingModel(String configName) {
        var vertexAiConfig = correspondingVertexAiConfig(configName);

        if (vertexAiConfig.enableIntegration()) {
            var embeddingModelConfig = vertexAiConfig.embeddingModel();
            String location = vertexAiConfig.location();
            if (DUMMY_KEY.equals(location)) {
                throw new ConfigValidationException(createConfigProblems("location", configName));
            }
            String projectId = vertexAiConfig.projectId();
            if (DUMMY_KEY.equals(projectId)) {
                throw new ConfigValidationException(createConfigProblems("project-id", configName));
            }
            Optional<String> baseUrl = vertexAiConfig.baseUrl()
                    .or(() -> Optional.of("https://" + location + "-aiplatform.googleapis.com"));

            var builder = VertexAiGeminiEmbeddingModel.builder()
                    .baseUrl(baseUrl)
                    .location(location)
                    .projectId(projectId)
                    .publisher(vertexAiConfig.publisher())
                    .modelId(embeddingModelConfig.modelId())
                    .logRequests(firstOrDefault(false, embeddingModelConfig.logRequests(), vertexAiConfig.logRequests()))
                    .logResponses(firstOrDefault(false, embeddingModelConfig.logResponses(), vertexAiConfig.logResponses()));

            vertexAiConfig.proxyHost().ifPresent(host -> {
                builder.proxy(new Proxy(
                        Type.valueOf(vertexAiConfig.proxyType()),
                        new InetSocketAddress(host, vertexAiConfig.proxyPort())));
            });

            builder.outputDimensionality(embeddingModelConfig.outputDimension().orElse(null));
            builder.taskType(embeddingModelConfig.taskType().orElse(null));

            return new Function<>() {
                @Override
                public EmbeddingModel apply(SyntheticCreationalContext<EmbeddingModel> context) {
                    ModelBuilderCustomizer.applyCustomizers(
                            context.getInjectedReference(EMBEDDING_MODEL_CUSTOMIZER_TYPE_LITERAL, Any.Literal.INSTANCE),
                            builder, configName);
                    return builder.build();
                }
            };
        } else {
            return new Function<>() {
                @Override
                public EmbeddingModel apply(SyntheticCreationalContext<EmbeddingModel> context) {
                    return new DisabledEmbeddingModel();
                }
            };
        }
    }

    public Function<SyntheticCreationalContext<ChatModel>, ChatModel> chatModel(String configName) {
        var vertexAiConfig = correspondingVertexAiConfig(configName);

        if (vertexAiConfig.enableIntegration()) {
            var chatModelConfig = vertexAiConfig.chatModel();
            Optional<String> baseUrl = vertexAiConfig.baseUrl();

            String location = vertexAiConfig.location();
            if (baseUrl.isEmpty() && DUMMY_KEY.equals(location)) {
                throw new ConfigValidationException(createConfigProblems("location", configName));
            }
            String projectId = vertexAiConfig.projectId();
            if (baseUrl.isEmpty() && DUMMY_KEY.equals(projectId)) {
                throw new ConfigValidationException(createConfigProblems("project-id", configName));
            }
            var builder = VertexAiGeminiChatLanguageModel.builder()
                    .baseUrl(baseUrl)
                    .location(location)
                    .projectId(projectId)
                    .publisher(vertexAiConfig.publisher())
                    .modelId(chatModelConfig.modelId())
                    .maxOutputTokens(chatModelConfig.maxOutputTokens())
                    .logRequests(firstOrDefault(false, chatModelConfig.logRequests(), vertexAiConfig.logRequests()))
                    .logResponses(firstOrDefault(false, chatModelConfig.logResponses(), vertexAiConfig.logResponses()));

            vertexAiConfig.proxyHost().ifPresent(host -> {
                builder.proxy(new Proxy(
                        Type.valueOf(vertexAiConfig.proxyType()),
                        new InetSocketAddress(host, vertexAiConfig.proxyPort())));
            });

            if (chatModelConfig.temperature().isPresent()) {
                builder.temperature(chatModelConfig.temperature().getAsDouble());
            }
            if (chatModelConfig.topK().isPresent()) {
                builder.topK(chatModelConfig.topK().getAsInt());
            }
            if (chatModelConfig.topP().isPresent()) {
                builder.topP(chatModelConfig.topP().getAsDouble());
            }
            if (chatModelConfig.timeout().isPresent()) {
                builder.timeout(chatModelConfig.timeout().get());
            }

            // TODO: add the rest of the properties
            return new Function<>() {
                @Override
                public ChatModel apply(SyntheticCreationalContext<ChatModel> context) {
                    builder.listeners(context.getInjectedReference(CHAT_MODEL_LISTENER_TYPE_LITERAL).stream()
                            .collect(Collectors.toList()));
                    ModelBuilderCustomizer.applyCustomizers(
                            context.getInjectedReference(CHAT_MODEL_CUSTOMIZER_TYPE_LITERAL, Any.Literal.INSTANCE),
                            builder, configName);
                    return builder.build();
                }
            };
        } else {
            return new Function<>() {
                @Override
                public ChatModel apply(SyntheticCreationalContext<ChatModel> context) {
                    return new DisabledChatModel();
                }
            };
        }

    }

    public Function<SyntheticCreationalContext<StreamingChatModel>, StreamingChatModel> streamingChatModel(String configName) {
        var vertexAiConfig = correspondingVertexAiConfig(configName);

        if (vertexAiConfig.enableIntegration()) {
            var chatModelConfig = vertexAiConfig.chatModel();
            Optional<String> baseUrl = vertexAiConfig.baseUrl();

            String location = vertexAiConfig.location();
            if (baseUrl.isEmpty() && DUMMY_KEY.equals(location)) {
                throw new ConfigValidationException(createConfigProblems("location", configName));
            }
            String projectId = vertexAiConfig.projectId();
            if (baseUrl.isEmpty() && DUMMY_KEY.equals(projectId)) {
                throw new ConfigValidationException(createConfigProblems("project-id", configName));
            }

            var builder = VertexAiGeminiStreamingChatLanguageModel.builder()
                    .baseUrl(baseUrl)
                    .location(location)
                    .projectId(projectId)
                    .publisher(vertexAiConfig.publisher())
                    .modelId(chatModelConfig.modelId())
                    .maxOutputTokens(chatModelConfig.maxOutputTokens())
                    .logRequests(firstOrDefault(false, chatModelConfig.logRequests(), vertexAiConfig.logRequests()))
                    .logResponses(firstOrDefault(false, chatModelConfig.logResponses(), vertexAiConfig.logResponses()));

            vertexAiConfig.proxyHost().ifPresent(host -> {
                builder.proxy(new Proxy(
                        Type.valueOf(vertexAiConfig.proxyType()),
                        new InetSocketAddress(host, vertexAiConfig.proxyPort())));
            });

            if (chatModelConfig.temperature().isPresent()) {
                builder.temperature(chatModelConfig.temperature().getAsDouble());
            }
            if (chatModelConfig.topK().isPresent()) {
                builder.topK(chatModelConfig.topK().getAsInt());
            }
            if (chatModelConfig.topP().isPresent()) {
                builder.topP(chatModelConfig.topP().getAsDouble());
            }
            if (chatModelConfig.timeout().isPresent()) {
                builder.timeout(chatModelConfig.timeout().get());
            }

            // TODO: add the rest of the properties
            return new Function<>() {
                @Override
                public StreamingChatModel apply(SyntheticCreationalContext<StreamingChatModel> context) {
                    builder.listeners(context.getInjectedReference(CHAT_MODEL_LISTENER_TYPE_LITERAL).stream()
                            .collect(Collectors.toList()));
                    ModelBuilderCustomizer.applyCustomizers(
                            context.getInjectedReference(STREAMING_CHAT_MODEL_CUSTOMIZER_TYPE_LITERAL, Any.Literal.INSTANCE),
                            builder, configName);
                    return builder.build();
                }
            };
        } else {
            return new Function<>() {
                @Override
                public StreamingChatModel apply(SyntheticCreationalContext<StreamingChatModel> context) {
                    return new DisabledStreamingChatModel();
                }
            };
        }
    }

    private LangChain4jVertexAiGeminiConfig.VertexAiGeminiConfig correspondingVertexAiConfig(String configName) {

        return NamedConfigUtil.isDefault(configName) ? runtimeConfig.getValue().defaultConfig()
                : runtimeConfig.getValue().namedConfig().get(configName);
    }

    private static ConfigValidationException.Problem[] createConfigProblems(String key, String configName) {
        return new ConfigValidationException.Problem[] { createConfigProblem(key, configName) };
    }

    private static ConfigValidationException.Problem createConfigProblem(String key, String configName) {
        return new ConfigValidationException.Problem(
                "SRCFG00014: The config property quarkus.langchain4j.vertexai.gemini%s%s is required but it could not be found in any config source"
                        .formatted(
                                NamedConfigUtil.isDefault(configName) ? "." : ("." + configName + "."), key));
    }
}
