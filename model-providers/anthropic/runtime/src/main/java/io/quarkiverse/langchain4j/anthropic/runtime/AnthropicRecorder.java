package io.quarkiverse.langchain4j.anthropic.runtime;

import static io.quarkiverse.langchain4j.runtime.OptionalUtil.firstOrDefault;

import java.time.Duration;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.util.TypeLiteral;

import org.jboss.logging.Logger;

import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.DisabledChatModel;
import dev.langchain4j.model.chat.DisabledStreamingChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import io.quarkiverse.langchain4j.anthropic.QuarkusAnthropicClient;
import io.quarkiverse.langchain4j.anthropic.runtime.config.ChatModelConfig;
import io.quarkiverse.langchain4j.anthropic.runtime.config.LangChain4jAnthropicConfig;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import io.smallrye.config.ConfigValidationException;

@Recorder
public class AnthropicRecorder {
    private static final Logger LOG = Logger.getLogger(AnthropicRecorder.class);

    private static final TypeLiteral<Instance<ChatModelListener>> CHAT_MODEL_LISTENER_TYPE_LITERAL = new TypeLiteral<>() {
    };

    private static final String DUMMY_KEY = "dummy";

    private final RuntimeValue<LangChain4jAnthropicConfig> runtimeConfig;

    public AnthropicRecorder(RuntimeValue<LangChain4jAnthropicConfig> runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
    }

    public Function<SyntheticCreationalContext<ChatModel>, ChatModel> chatModel(String configName) {
        var anthropicConfig = correspondingAnthropicConfig(runtimeConfig.getValue(), configName);

        if (anthropicConfig.enableIntegration()) {
            var chatModelConfig = anthropicConfig.chatModel();
            var apiKey = anthropicConfig.apiKey();

            if (DUMMY_KEY.equals(apiKey)) {
                throw new ConfigValidationException(createApiKeyConfigProblem(configName));
            }

            var builder = AnthropicChatModel.builder()
                    .baseUrl(anthropicConfig.baseUrl())
                    .apiKey(apiKey)
                    .version(anthropicConfig.version())
                    .modelName(chatModelConfig.modelName())
                    .logRequests(firstOrDefault(false, chatModelConfig.logRequests(), anthropicConfig.logRequests()))
                    .logResponses(firstOrDefault(false, chatModelConfig.logResponses(), anthropicConfig.logResponses()))
                    .timeout(anthropicConfig.timeout().orElse(Duration.ofSeconds(10)))
                    .maxTokens(chatModelConfig.maxTokens())
                    .maxRetries(chatModelConfig.maxRetries());

            if (chatModelConfig.temperature().isPresent()) {
                builder.temperature(chatModelConfig.temperature().getAsDouble());
            }

            builder.topK(chatModelConfig.topK().orElse(40));

            if (chatModelConfig.topP().isPresent()) {
                builder.topP(chatModelConfig.topP().getAsDouble());
            }

            if (chatModelConfig.stopSequences().isPresent()) {
                builder.stopSequences(chatModelConfig.stopSequences().get());
            }

            ChatModelConfig.ThinkingConfig thinkingConfig = chatModelConfig.thinking();
            if (thinkingConfig.type().isPresent()) {
                if (chatModelConfig.topK().isPresent()) {
                    LOG.warn("TopK was not configured because thinking was enabled");
                }
                builder.topK(null);
                builder.thinkingType(thinkingConfig.type().get());
            }

            if (thinkingConfig.budgetTokens().isPresent()) {
                builder.thinkingBudgetTokens(thinkingConfig.budgetTokens().get());
            }

            if (thinkingConfig.returnThinking().isPresent()) {
                builder.returnThinking(thinkingConfig.returnThinking().get());
            }

            if (thinkingConfig.sendThinking().isPresent()) {
                builder.sendThinking(thinkingConfig.sendThinking().get());
            }

            // Pass caching configuration to builder
            builder.cacheSystemMessages(chatModelConfig.cacheSystemMessages());
            builder.cacheTools(chatModelConfig.cacheTools());

            // Add beta header for interleaved thinking if enabled
            if (thinkingConfig.interleaved().orElse(false)) {
                builder.beta("interleaved-thinking-2025-05-14");
            }

            var logCurl = firstOrDefault(false, anthropicConfig.logRequestsCurl());

            return new Function<>() {
                @Override
                public ChatModel apply(SyntheticCreationalContext<ChatModel> context) {
                    builder.listeners(context.getInjectedReference(CHAT_MODEL_LISTENER_TYPE_LITERAL).stream()
                            .collect(Collectors.toList()));
                    QuarkusAnthropicClient.setLogCurlHint(logCurl);
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
        var anthropicConfig = correspondingAnthropicConfig(runtimeConfig.getValue(), configName);

        if (anthropicConfig.enableIntegration()) {
            var chatModelConfig = anthropicConfig.chatModel();
            var apiKey = anthropicConfig.apiKey();

            if (DUMMY_KEY.equals(apiKey)) {
                throw new ConfigValidationException(createApiKeyConfigProblem(configName));
            }

            var builder = AnthropicStreamingChatModel.builder()
                    .baseUrl(anthropicConfig.baseUrl())
                    .apiKey(apiKey)
                    .version(anthropicConfig.version())
                    .modelName(chatModelConfig.modelName())
                    .logRequests(firstOrDefault(false, chatModelConfig.logRequests(), anthropicConfig.logRequests()))
                    .logResponses(firstOrDefault(false, chatModelConfig.logResponses(), anthropicConfig.logResponses()))
                    .timeout(anthropicConfig.timeout().orElse(Duration.ofSeconds(10)))
                    .topK(chatModelConfig.topK().orElse(40))
                    .maxTokens(chatModelConfig.maxTokens());

            if (chatModelConfig.temperature().isPresent()) {
                builder.temperature(chatModelConfig.temperature().getAsDouble());
            }

            if (chatModelConfig.topP().isPresent()) {
                builder.topP(chatModelConfig.topP().getAsDouble());
            }

            if (chatModelConfig.stopSequences().isPresent()) {
                builder.stopSequences(chatModelConfig.stopSequences().get());
            }

            ChatModelConfig.ThinkingConfig thinkingConfig = chatModelConfig.thinking();
            if (thinkingConfig.type().isPresent()) {
                if (chatModelConfig.topK().isPresent()) {
                    LOG.warn("TopK was not configured because thinking was enabled");
                }
                builder.topK(null);
                builder.thinkingType(thinkingConfig.type().get());
            }

            if (thinkingConfig.budgetTokens().isPresent()) {
                builder.thinkingBudgetTokens(thinkingConfig.budgetTokens().get());
            }

            if (thinkingConfig.returnThinking().isPresent()) {
                builder.returnThinking(thinkingConfig.returnThinking().get());
            }

            if (thinkingConfig.sendThinking().isPresent()) {
                builder.sendThinking(thinkingConfig.sendThinking().get());
            }

            // Pass caching configuration to builder
            builder.cacheSystemMessages(chatModelConfig.cacheSystemMessages());
            builder.cacheTools(chatModelConfig.cacheTools());

            // Add beta header for interleaved thinking if enabled
            if (thinkingConfig.interleaved().orElse(false)) {
                builder.beta("interleaved-thinking-2025-05-14");
            }

            var logCurl = firstOrDefault(false, anthropicConfig.logRequestsCurl());

            return new Function<>() {
                @Override
                public StreamingChatModel apply(SyntheticCreationalContext<StreamingChatModel> context) {
                    builder.listeners(context.getInjectedReference(CHAT_MODEL_LISTENER_TYPE_LITERAL).stream()
                            .collect(Collectors.toList()));
                    QuarkusAnthropicClient.setLogCurlHint(logCurl);
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

    private LangChain4jAnthropicConfig.AnthropicConfig correspondingAnthropicConfig(
            LangChain4jAnthropicConfig runtimeConfig, String configName) {

        return NamedConfigUtil.isDefault(configName) ? runtimeConfig.defaultConfig()
                : runtimeConfig.namedConfig().get(configName);
    }

    private static ConfigValidationException.Problem[] createApiKeyConfigProblem(String configName) {
        return createConfigProblems("api-key", configName);
    }

    private static ConfigValidationException.Problem[] createConfigProblems(String key, String configName) {
        return new ConfigValidationException.Problem[] { createConfigProblem(key, configName) };
    }

    private static ConfigValidationException.Problem createConfigProblem(String key, String configName) {
        return new ConfigValidationException.Problem(
                "SRCFG00014: The config property quarkus.langchain4j.anthropic%s%s is required but it could not be found in any config source"
                        .formatted(
                                NamedConfigUtil.isDefault(configName) ? "." : ("." + configName + "."), key));
    }
}
