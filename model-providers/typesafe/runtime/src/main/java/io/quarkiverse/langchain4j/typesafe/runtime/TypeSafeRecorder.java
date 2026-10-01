package io.quarkiverse.langchain4j.typesafe.runtime;

import java.util.function.Supplier;

import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.typesafe.TypeSafeDecisionModel;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkiverse.langchain4j.typesafe.runtime.config.LangChain4jTypeSafeConfig;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class TypeSafeRecorder {

    private final RuntimeValue<LangChain4jTypeSafeConfig> runtimeConfig;

    public TypeSafeRecorder(RuntimeValue<LangChain4jTypeSafeConfig> runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
    }

    public Supplier<DecisionModel> typeSafeDecisionModelSupplier(String configName) {
        LangChain4jTypeSafeConfig.TypeSafeConfig config = correspondingTypeSafeConfig(configName);

        return new Supplier<>() {
            @Override
            public DecisionModel get() {
                var builder = TypeSafeDecisionModel.builder()
                        .baseUrl(config.baseUrl())
                        .modelName(config.decisionModel().modelName())
                        .httpClientBuilder(new dev.langchain4j.http.client.jdk.JdkHttpClientBuilder())
                        .timeout(config.decisionModel().timeout())
                        .maxRetries(config.decisionModel().maxRetries());

                if (config.apiKey().isPresent() && !config.apiKey().get().isBlank()) {
                    builder.apiKey(config.apiKey().get());
                }

                config.decisionModel().logRequests().ifPresent(builder::logRequests);
                config.decisionModel().logResponses().ifPresent(builder::logResponses);

                if (config.decisionModel().customHeaders() != null && !config.decisionModel().customHeaders().isEmpty()) {
                    builder.customHeaders(config.decisionModel().customHeaders());
                }

                return builder.build();
            }
        };
    }

    private LangChain4jTypeSafeConfig.TypeSafeConfig correspondingTypeSafeConfig(String configName) {
        LangChain4jTypeSafeConfig currentConfig = runtimeConfig.getValue();
        if (NamedConfigUtil.isDefault(configName)) {
            return currentConfig.defaultConfig();
        }
        return currentConfig.namedConfig().get(configName);
    }
}
