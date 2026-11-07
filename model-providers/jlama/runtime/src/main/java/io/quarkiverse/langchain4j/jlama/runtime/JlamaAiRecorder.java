package io.quarkiverse.langchain4j.jlama.runtime;

import java.util.function.Function;

import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.util.TypeLiteral;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.DisabledChatModel;
import dev.langchain4j.model.chat.DisabledStreamingChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.DisabledEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.quarkiverse.langchain4j.ModelBuilderCustomizer;
import io.quarkiverse.langchain4j.jlama.JlamaChatModel;
import io.quarkiverse.langchain4j.jlama.JlamaEmbeddingModel;
import io.quarkiverse.langchain4j.jlama.JlamaStreamingChatModel;
import io.quarkiverse.langchain4j.jlama.runtime.config.ChatModelConfig;
import io.quarkiverse.langchain4j.jlama.runtime.config.LangChain4jJlamaConfig;
import io.quarkiverse.langchain4j.jlama.runtime.config.LangChain4jJlamaFixedRuntimeConfig;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class JlamaAiRecorder {
    private static final TypeLiteral<Instance<ModelBuilderCustomizer<JlamaChatModel.JlamaChatModelBuilder>>> CHAT_MODEL_CUSTOMIZER_TYPE_LITERAL = new TypeLiteral<>() {
    };
    private static final TypeLiteral<Instance<ModelBuilderCustomizer<JlamaStreamingChatModel.JlamaStreamingChatModelBuilder>>> STREAMING_CHAT_MODEL_CUSTOMIZER_TYPE_LITERAL = new TypeLiteral<>() {
    };
    private static final TypeLiteral<Instance<ModelBuilderCustomizer<JlamaEmbeddingModel.JlamaEmbeddingModelBuilder>>> EMBEDDING_MODEL_CUSTOMIZER_TYPE_LITERAL = new TypeLiteral<>() {
    };

    private final RuntimeValue<LangChain4jJlamaConfig> runtimeConfig;
    private final RuntimeValue<LangChain4jJlamaFixedRuntimeConfig> fixedRuntimeConfig;

    public JlamaAiRecorder(RuntimeValue<LangChain4jJlamaConfig> runtimeConfig,
            RuntimeValue<LangChain4jJlamaFixedRuntimeConfig> fixedRuntimeConfig) {
        this.runtimeConfig = runtimeConfig;
        this.fixedRuntimeConfig = fixedRuntimeConfig;
    }

    public Function<SyntheticCreationalContext<ChatModel>, ChatModel> chatModel(String configName) {
        LangChain4jJlamaConfig.JlamaConfig jlamaConfig = correspondingJlamaConfig(configName);
        LangChain4jJlamaFixedRuntimeConfig.JlamaConfig jlamaFixedRuntimeConfig = correspondingJlamaFixedRuntimeConfig(
                configName);

        if (jlamaConfig.enableIntegration()) {
            ChatModelConfig chatModelConfig = jlamaConfig.chatModel();

            String modelName = jlamaFixedRuntimeConfig.chatModel().modelName();
            var builder = JlamaChatModel.builder()
                    .modelName(modelName)
                    .modelCachePath(fixedRuntimeConfig.getValue().modelsPath());

            jlamaConfig.logRequests().ifPresent(builder::logRequests);
            jlamaConfig.logResponses().ifPresent(builder::logResponses);

            chatModelConfig.temperature().ifPresent(temp -> builder.temperature((float) temp));
            chatModelConfig.maxTokens().ifPresent(builder::maxTokens);

            return new Function<>() {
                @Override
                public ChatModel apply(SyntheticCreationalContext<ChatModel> context) {
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
        LangChain4jJlamaConfig.JlamaConfig jlamaConfig = correspondingJlamaConfig(configName);
        LangChain4jJlamaFixedRuntimeConfig.JlamaConfig jlamaFixedRuntimeConfig = correspondingJlamaFixedRuntimeConfig(
                configName);
        if (jlamaConfig.enableIntegration()) {
            ChatModelConfig chatModelConfig = jlamaConfig.chatModel();

            var builder = JlamaStreamingChatModel.builder()
                    .modelName(jlamaFixedRuntimeConfig.chatModel().modelName())
                    .modelCachePath(fixedRuntimeConfig.getValue().modelsPath());

            chatModelConfig.temperature().ifPresent(temp -> builder.temperature((float) temp));

            return new Function<>() {
                @Override
                public StreamingChatModel apply(SyntheticCreationalContext<StreamingChatModel> context) {
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

    public Function<SyntheticCreationalContext<EmbeddingModel>, EmbeddingModel> embeddingModel(String configName) {
        LangChain4jJlamaConfig.JlamaConfig jlamaConfig = correspondingJlamaConfig(configName);
        LangChain4jJlamaFixedRuntimeConfig.JlamaConfig jlamaFixedRuntimeConfig = correspondingJlamaFixedRuntimeConfig(
                configName);

        if (jlamaConfig.enableIntegration()) {
            var builder = JlamaEmbeddingModel.builder()
                    .modelName(jlamaFixedRuntimeConfig.embeddingModel().modelName())
                    .modelCachePath(fixedRuntimeConfig.getValue().modelsPath());

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

    private LangChain4jJlamaConfig.JlamaConfig correspondingJlamaConfig(String configName) {
        return NamedConfigUtil.isDefault(configName) ? runtimeConfig.getValue().defaultConfig()
                : runtimeConfig.getValue().namedConfig().get(configName);
    }

    private LangChain4jJlamaFixedRuntimeConfig.JlamaConfig correspondingJlamaFixedRuntimeConfig(
            String configName) {
        return NamedConfigUtil.isDefault(configName) ? fixedRuntimeConfig.getValue().defaultConfig()
                : fixedRuntimeConfig.getValue().namedConfig().get(configName);
    }

}
