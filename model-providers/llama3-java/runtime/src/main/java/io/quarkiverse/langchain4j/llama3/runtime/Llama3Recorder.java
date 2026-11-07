package io.quarkiverse.langchain4j.llama3.runtime;

import java.util.function.Function;

import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.util.TypeLiteral;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.DisabledChatModel;
import dev.langchain4j.model.chat.DisabledStreamingChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import io.quarkiverse.langchain4j.ModelBuilderCustomizer;
import io.quarkiverse.langchain4j.llama3.Llama3ChatModel;
import io.quarkiverse.langchain4j.llama3.Llama3StreamingChatModel;
import io.quarkiverse.langchain4j.llama3.runtime.config.ChatModelConfig;
import io.quarkiverse.langchain4j.llama3.runtime.config.LangChain4jLlama3FixedRuntimeConfig;
import io.quarkiverse.langchain4j.llama3.runtime.config.LangChain4jLlama3RuntimeConfig;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class Llama3Recorder {

    private static final TypeLiteral<Instance<ModelBuilderCustomizer<Llama3ChatModel.Builder>>> CHAT_MODEL_CUSTOMIZER_TYPE_LITERAL = new TypeLiteral<>() {
    };
    private static final TypeLiteral<Instance<ModelBuilderCustomizer<Llama3StreamingChatModel.Builder>>> STREAMING_CHAT_MODEL_CUSTOMIZER_TYPE_LITERAL = new TypeLiteral<>() {
    };

    private final RuntimeValue<LangChain4jLlama3RuntimeConfig> runtimeConfig;
    private final RuntimeValue<LangChain4jLlama3FixedRuntimeConfig> fixedRuntimeConfig;

    public Llama3Recorder(RuntimeValue<LangChain4jLlama3RuntimeConfig> runtimeConfig,
            RuntimeValue<LangChain4jLlama3FixedRuntimeConfig> fixedRuntimeConfig) {
        this.runtimeConfig = runtimeConfig;
        this.fixedRuntimeConfig = fixedRuntimeConfig;
    }

    public Function<SyntheticCreationalContext<ChatModel>, ChatModel> chatModel(String configName) {
        LangChain4jLlama3RuntimeConfig.Llama3Config llama3Config = correspondingJlamaConfig(configName);
        LangChain4jLlama3FixedRuntimeConfig.Llama3Config llama3FixedRuntimeConfig = correspondingJlamaFixedRuntimeConfig(
                configName);

        if (llama3Config.enableIntegration()) {
            ChatModelConfig chatModelConfig = llama3Config.chatModel();

            var builder = Llama3ChatModel.builder()
                    .modelName(llama3FixedRuntimeConfig.chatModel().modelName())
                    .quantization(llama3FixedRuntimeConfig.chatModel().quantization())
                    .logRequests(llama3Config.logRequests().orElse(false))
                    .logResponses(llama3Config.logResponses().orElse(false))
                    .modelCachePath(fixedRuntimeConfig.getValue().modelsPath());

            if (chatModelConfig.temperature().isPresent()) {
                builder.temperature((float) chatModelConfig.temperature().getAsDouble());
            }
            if (chatModelConfig.maxTokens().isPresent()) {
                builder.maxTokens(chatModelConfig.maxTokens().getAsInt());
            }

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

    public Function<SyntheticCreationalContext<StreamingChatModel>, StreamingChatModel> streamingChatModel(
            String configName) {
        LangChain4jLlama3RuntimeConfig.Llama3Config llama3Config = correspondingJlamaConfig(configName);
        LangChain4jLlama3FixedRuntimeConfig.Llama3Config llama3FixedRuntimeConfig = correspondingJlamaFixedRuntimeConfig(
                configName);

        if (llama3Config.enableIntegration()) {
            ChatModelConfig chatModelConfig = llama3Config.chatModel();

            var builder = Llama3StreamingChatModel.builder()
                    .modelName(llama3FixedRuntimeConfig.chatModel().modelName())
                    .quantization(llama3FixedRuntimeConfig.chatModel().quantization())
                    .logRequests(llama3Config.logRequests().orElse(false))
                    .logResponses(llama3Config.logResponses().orElse(false))
                    .modelCachePath(fixedRuntimeConfig.getValue().modelsPath());

            if (chatModelConfig.temperature().isPresent()) {
                builder.temperature((float) chatModelConfig.temperature().getAsDouble());
            }
            if (chatModelConfig.maxTokens().isPresent()) {
                builder.maxTokens(chatModelConfig.maxTokens().getAsInt());
            }

            return new Function<>() {
                @Override
                public StreamingChatModel apply(SyntheticCreationalContext<StreamingChatModel> context) {
                    ModelBuilderCustomizer.applyCustomizers(
                            context.getInjectedReference(STREAMING_CHAT_MODEL_CUSTOMIZER_TYPE_LITERAL,
                                    Any.Literal.INSTANCE),
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

    private LangChain4jLlama3RuntimeConfig.Llama3Config correspondingJlamaConfig(String configName) {
        LangChain4jLlama3RuntimeConfig.Llama3Config llama3Config;
        if (NamedConfigUtil.isDefault(configName)) {
            llama3Config = runtimeConfig.getValue().defaultConfig();
        } else {
            llama3Config = runtimeConfig.getValue().namedConfig().get(configName);
        }
        return llama3Config;
    }

    private LangChain4jLlama3FixedRuntimeConfig.Llama3Config correspondingJlamaFixedRuntimeConfig(
            String configName) {
        LangChain4jLlama3FixedRuntimeConfig.Llama3Config llama3Config;
        if (NamedConfigUtil.isDefault(configName)) {
            llama3Config = fixedRuntimeConfig.getValue().defaultConfig();
        } else {
            llama3Config = fixedRuntimeConfig.getValue().namedConfig().get(configName);
        }
        return llama3Config;
    }

}
