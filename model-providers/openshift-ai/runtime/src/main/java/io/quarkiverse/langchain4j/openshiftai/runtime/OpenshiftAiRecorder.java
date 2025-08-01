package io.quarkiverse.langchain4j.openshiftai.runtime;

import static io.quarkiverse.langchain4j.runtime.OptionalUtil.firstOrDefault;

import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.DisabledChatModel;
import io.quarkiverse.langchain4j.openshiftai.OpenshiftAiChatModel;
import io.quarkiverse.langchain4j.openshiftai.runtime.config.ChatModelConfig;
import io.quarkiverse.langchain4j.openshiftai.runtime.config.LangChain4jOpenshiftAiConfig;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import io.smallrye.config.ConfigValidationException;

@Recorder
public class OpenshiftAiRecorder {

    private static final String DUMMY_URL = "https://dummy.ai/api";
    private static final String DUMMY_MODEL_ID = "dummy";
    public static final ConfigValidationException.Problem[] EMPTY_PROBLEMS = new ConfigValidationException.Problem[0];

    private final RuntimeValue<LangChain4jOpenshiftAiConfig> runtimeConfig;

    public OpenshiftAiRecorder(RuntimeValue<LangChain4jOpenshiftAiConfig> runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
    }

    public Supplier<ChatModel> chatModel(String configName) {
        LangChain4jOpenshiftAiConfig.OpenshiftAiConfig openshiftAiConfig = correspondingOpenshiftAiConfig(
                runtimeConfig.getValue(), configName);

        if (openshiftAiConfig.enableIntegration()) {
            ChatModelConfig chatModelConfig = openshiftAiConfig.chatModel();

            List<ConfigValidationException.Problem> configProblems = new ArrayList<>();
            URL baseUrl = openshiftAiConfig.baseUrl();

            if (DUMMY_URL.equals(baseUrl.toString())) {
                configProblems.add(createBaseURLConfigProblem(configName));
            }

            String modelId = chatModelConfig.modelId();
            if (DUMMY_MODEL_ID.equals(modelId)) {
                configProblems.add(createModelIdConfigProblem(configName));
            }

            if (!configProblems.isEmpty()) {
                throw new ConfigValidationException(configProblems.toArray(EMPTY_PROBLEMS));
            }

            var builder = OpenshiftAiChatModel.builder()
                    .url(baseUrl)
                    .timeout(openshiftAiConfig.timeout().orElse(Duration.ofSeconds(10)))
                    .logRequests(firstOrDefault(false, chatModelConfig.logRequests(), openshiftAiConfig.logRequests()))
                    .logResponses(firstOrDefault(false, chatModelConfig.logResponses(), openshiftAiConfig.logResponses()))
                    .modelId(modelId);

            return new Supplier<>() {
                @Override
                public ChatModel get() {
                    return builder.build();
                }
            };
        } else {
            return new Supplier<>() {
                @Override
                public ChatModel get() {
                    return new DisabledChatModel();
                }
            };
        }
    }

    private LangChain4jOpenshiftAiConfig.OpenshiftAiConfig correspondingOpenshiftAiConfig(
            LangChain4jOpenshiftAiConfig runtimeConfig,
            String configName) {
        LangChain4jOpenshiftAiConfig.OpenshiftAiConfig openshiftAiConfig;
        if (NamedConfigUtil.isDefault(configName)) {
            openshiftAiConfig = runtimeConfig.defaultConfig();
        } else {
            openshiftAiConfig = runtimeConfig.namedConfig().get(configName);
        }
        return openshiftAiConfig;
    }

    private ConfigValidationException.Problem createBaseURLConfigProblem(String configName) {
        return createConfigProblem("base-url", configName);
    }

    private ConfigValidationException.Problem createModelIdConfigProblem(String configName) {
        return createConfigProblem("chat-model.model-id", configName);
    }

    private static ConfigValidationException.Problem createConfigProblem(String key, String configName) {
        return new ConfigValidationException.Problem(String.format(
                "SRCFG00014: The config property quarkus.langchain4j.openshift-ai%s%s is required but it could not be found in any config source",
                NamedConfigUtil.isDefault(configName) ? "." : ("." + configName + "."), key));
    }
}
