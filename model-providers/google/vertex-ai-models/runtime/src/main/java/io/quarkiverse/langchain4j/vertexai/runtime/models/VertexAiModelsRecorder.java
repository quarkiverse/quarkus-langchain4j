package io.quarkiverse.langchain4j.vertexai.runtime.models;

import static io.quarkiverse.langchain4j.runtime.OptionalUtil.firstOrDefault;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Proxy.Type;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.util.TypeLiteral;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.DisabledChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkiverse.langchain4j.vertexai.runtime.models.config.LangChain4jVertexAiConfig;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import io.smallrye.config.ConfigValidationException;

@Recorder
public class VertexAiModelsRecorder {

    private static final TypeLiteral<Instance<ChatModelListener>> CHAT_MODEL_LISTENER_TYPE_LITERAL = new TypeLiteral<>() {
    };

    private final RuntimeValue<LangChain4jVertexAiConfig> runtimeConfig;

    public VertexAiModelsRecorder(RuntimeValue<LangChain4jVertexAiConfig> runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
    }

    public Function<SyntheticCreationalContext<ChatModel>, ChatModel> chatModel(String configName) {
        var vertexAiConfig = correspondingVertexAiConfig(configName);

        if (vertexAiConfig.enableIntegration()) {
            var chatModelConfig = vertexAiConfig.chatModel();

            // The base-url is calculated according to the location provided: https://<location>-aiplatform.googleapis.com
            // and should not be provided except for testing purposes
            Optional<String> baseUrl = vertexAiConfig.baseUrl();

            String location = vertexAiConfig.location();
            if (location.isEmpty()) {
                throw new ConfigValidationException(createConfigProblems("location", configName));
            }

            String projectId = vertexAiConfig.projectId();
            if (projectId.isEmpty()) {
                throw new ConfigValidationException(createConfigProblems("project-id", configName));
            }

            String modelId = vertexAiConfig.modelId();
            if (modelId.isEmpty()) {
                throw new ConfigValidationException(createConfigProblems("model-id", configName));
            }

            var builder = VertexAiChatModel.builder()
                    .baseUrl(baseUrl)
                    .projectId(projectId)
                    .location(location)
                    .publisher(vertexAiConfig.publisher())
                    .modelId(modelId)
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

            if (chatModelConfig.thinking().includeThoughts()) {
                builder.includeThoughts(true);
                builder.thinkingBudget(chatModelConfig.thinking().thinkingBudget().get());
            }

            return new Function<>() {
                @Override
                public ChatModel apply(SyntheticCreationalContext<ChatModel> context) {
                    builder.listeners(context.getInjectedReference(CHAT_MODEL_LISTENER_TYPE_LITERAL).stream()
                            .collect(Collectors.toList()));
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
