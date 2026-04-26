package io.quarkiverse.langchain4j.vertexai.runtime;

import static io.quarkiverse.langchain4j.runtime.OptionalUtil.firstOrDefault;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Proxy.Type;
import java.util.Optional;
import java.util.function.Function;

import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.util.TypeLiteral;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.DisabledChatModel;
import io.quarkiverse.langchain4j.ModelBuilderCustomizer;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkiverse.langchain4j.vertexai.runtime.config.LangChain4jVertexAiConfig;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import io.smallrye.config.ConfigValidationException;

@Recorder
public class VertexAiRecorder {
    private static final String DUMMY_KEY = "dummy";
    private static final TypeLiteral<Instance<ModelBuilderCustomizer<VertexAiChatLanguageModel.Builder>>> CHAT_MODEL_CUSTOMIZER_TYPE_LITERAL = new TypeLiteral<>() {
    };

    private final RuntimeValue<LangChain4jVertexAiConfig> runtimeConfig;

    public VertexAiRecorder(RuntimeValue<LangChain4jVertexAiConfig> runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
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
            var builder = VertexAiChatLanguageModel.builder()
                    .baseUrl(baseUrl)
                    .location(location)
                    .projectId(projectId)
                    .publisher(vertexAiConfig.publisher())
                    .modelId(chatModelConfig.modelId())
                    .maxOutputTokens(chatModelConfig.maxOutputTokens())
                    .temperature(chatModelConfig.temperature())
                    .topK(chatModelConfig.topK())
                    .topP(chatModelConfig.topP())
                    .logRequests(firstOrDefault(false, chatModelConfig.logRequests(), vertexAiConfig.logRequests()))
                    .logResponses(firstOrDefault(false, chatModelConfig.logResponses(), vertexAiConfig.logResponses()));

            vertexAiConfig.proxyHost().ifPresent(host -> {
                builder.proxy(new Proxy(
                        Type.valueOf(vertexAiConfig.proxyType()),
                        new InetSocketAddress(host, vertexAiConfig.proxyPort())));
            });

            // TODO: add the rest of the properties

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

    private LangChain4jVertexAiConfig.VertexAiConfig correspondingVertexAiConfig(String configName) {

        return NamedConfigUtil.isDefault(configName) ? runtimeConfig.getValue().defaultConfig()
                : runtimeConfig.getValue().namedConfig().get(configName);
    }

    private static ConfigValidationException.Problem[] createConfigProblems(String key, String configName) {
        return new ConfigValidationException.Problem[] { createConfigProblem(key, configName) };
    }

    private static ConfigValidationException.Problem createConfigProblem(String key, String configName) {
        return new ConfigValidationException.Problem(
                "SRCFG00014: The config property quarkus.langchain4j.vertexai%s%s is required but it could not be found in any config source"
                        .formatted(
                                NamedConfigUtil.isDefault(configName) ? "." : ("." + configName + "."), key));
    }
}
