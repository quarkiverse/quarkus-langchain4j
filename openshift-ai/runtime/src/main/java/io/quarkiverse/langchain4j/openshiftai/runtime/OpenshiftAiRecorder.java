package io.quarkiverse.langchain4j.openshiftai.runtime;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.DisabledChatLanguageModel;
import io.quarkiverse.langchain4j.openshiftai.OpenshiftAiChatModel;
import io.quarkiverse.langchain4j.openshiftai.runtime.config.ChatModelConfig;
import io.quarkiverse.langchain4j.openshiftai.runtime.config.LangChain4jOpenshiftAiConfig;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.runtime.annotations.Recorder;
import io.smallrye.config.ConfigValidationException;

@Recorder
public class OpenshiftAiRecorder {

    private static final String DUMMY_URL = "https://dummy.ai/api";
    private static final String DUMMY_MODEL_ID = "dummy";
    public static final ConfigValidationException.Problem[] EMPTY_PROBLEMS = new ConfigValidationException.Problem[0];

    public Supplier<ChatLanguageModel> chatModel(LangChain4jOpenshiftAiConfig runtimeConfig, String configName) {
        LangChain4jOpenshiftAiConfig.OpenshiftAiConfig openshiftAiConfig = correspondingOpenshiftAiConfig(runtimeConfig,
                configName);

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
                    .timeout(openshiftAiConfig.timeout())
                    .logRequests(chatModelConfig.logRequests().orElse(false))
                    .logResponses(chatModelConfig.logResponses().orElse(false))
                    .modelId(modelId);

            return new Supplier<>() {
                @Override
                public ChatLanguageModel get() {
                    return builder.build();
                }
            };
        } else {
            return new Supplier<>() {
                @Override
                public ChatLanguageModel get() {
                    return new DisabledChatLanguageModel();
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
