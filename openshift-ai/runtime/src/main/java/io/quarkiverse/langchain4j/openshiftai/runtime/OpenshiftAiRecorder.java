package io.quarkiverse.langchain4j.openshiftai.runtime;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import io.quarkiverse.langchain4j.openshiftai.OpenshiftAiChatModel;
import io.quarkiverse.langchain4j.openshiftai.runtime.config.ChatModelConfig;
import io.quarkiverse.langchain4j.openshiftai.runtime.config.Langchain4jOpenshiftAiConfig;
import io.quarkiverse.langchain4j.runtime.NamedModelUtil;
import io.quarkus.runtime.annotations.Recorder;
import io.smallrye.config.ConfigValidationException;

@Recorder
public class OpenshiftAiRecorder {

    private static final String DUMMY_URL = "https://dummy.ai/api";
    private static final String DUMMY_MODEL_ID = "dummy";
    public static final ConfigValidationException.Problem[] EMPTY_PROBLEMS = new ConfigValidationException.Problem[0];

    public Supplier<?> chatModel(Langchain4jOpenshiftAiConfig runtimeConfig, String modelName) {
        Langchain4jOpenshiftAiConfig.OpenshiftAiConfig openshiftAiConfig = correspondingOpenshiftAiConfig(runtimeConfig,
                modelName);
        ChatModelConfig chatModelConfig = openshiftAiConfig.chatModel();

        List<ConfigValidationException.Problem> configProblems = new ArrayList<>();
        URL baseUrl = openshiftAiConfig.baseUrl();
        if (DUMMY_URL.equals(baseUrl.toString())) {
            configProblems.add(createBaseURLConfigProblem(modelName));
        }
        String modelId = chatModelConfig.modelId();
        if (DUMMY_MODEL_ID.equals(modelId)) {
            configProblems.add(createModelIdConfigProblem(modelName));
        }

        if (!configProblems.isEmpty()) {
            throw new ConfigValidationException(configProblems.toArray(EMPTY_PROBLEMS));
        }

        var builder = OpenshiftAiChatModel.builder()
                .url(baseUrl)
                .timeout(openshiftAiConfig.timeout())
                .logRequests(openshiftAiConfig.logRequests())
                .logResponses(openshiftAiConfig.logResponses())

                .modelId(modelId);

        return new Supplier<>() {
            @Override
            public Object get() {
                return builder.build();
            }
        };
    }

    private Langchain4jOpenshiftAiConfig.OpenshiftAiConfig correspondingOpenshiftAiConfig(
            Langchain4jOpenshiftAiConfig runtimeConfig,
            String modelName) {
        Langchain4jOpenshiftAiConfig.OpenshiftAiConfig openshiftAiConfig;
        if (NamedModelUtil.isDefault(modelName)) {
            openshiftAiConfig = runtimeConfig.defaultConfig();
        } else {
            openshiftAiConfig = runtimeConfig.namedConfig().get(modelName);
        }
        return openshiftAiConfig;
    }

    private ConfigValidationException.Problem createBaseURLConfigProblem(String modelName) {
        return createConfigProblem("base-url", modelName);
    }

    private ConfigValidationException.Problem createModelIdConfigProblem(String modelName) {
        return createConfigProblem("chat-model.model-id", modelName);
    }

    private static ConfigValidationException.Problem createConfigProblem(String key, String modelName) {
        return new ConfigValidationException.Problem(String.format(
                "SRCFG00014: The config property quarkus.langchain4j.openshift-ai%s%s is required but it could not be found in any config source",
                NamedModelUtil.isDefault(modelName) ? "." : ("." + modelName + "."), key));
    }
}
