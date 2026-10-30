package io.quarkiverse.langchain4j.typesafe.runtime;

import static io.quarkiverse.langchain4j.runtime.OptionalUtil.firstOrDefault;

import java.time.Duration;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.util.TypeLiteral;

import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.listener.DecisionModelListener;
import dev.langchain4j.model.typesafe.TypeSafeDecisionModel;
import io.quarkiverse.langchain4j.ModelBuilderCustomizer;
import io.quarkiverse.langchain4j.jaxrsclient.JaxRsHttpClientBuilder;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkiverse.langchain4j.typesafe.runtime.config.DecisionModelConfig;
import io.quarkiverse.langchain4j.typesafe.runtime.config.LangChain4jTypeSafeConfig;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import io.smallrye.config.ConfigValidationException;

@Recorder
public class TypeSafeRecorder {

    private static final String DEFAULT_BASE_URL = "https://api.typesafe.ai";

    private static final TypeLiteral<Instance<DecisionModelListener>> DECISION_MODEL_LISTENER_TYPE_LITERAL = new TypeLiteral<>() {
    };
    private static final TypeLiteral<Instance<ModelBuilderCustomizer<TypeSafeDecisionModel.TypeSafeDecisionModelBuilder>>> DECISION_MODEL_CUSTOMIZER_TYPE_LITERAL = new TypeLiteral<>() {
    };

    private final RuntimeValue<LangChain4jTypeSafeConfig> runtimeConfig;

    public TypeSafeRecorder(RuntimeValue<LangChain4jTypeSafeConfig> runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
    }

    public Function<SyntheticCreationalContext<DecisionModel>, DecisionModel> decisionModel(String configName) {
        LangChain4jTypeSafeConfig.TypeSafeConfig typeSafeConfig = correspondingTypeSafeConfig(configName);

        // the API key is only optional for other servers implementing the same API
        if (typeSafeConfig.apiKey().isEmpty() && DEFAULT_BASE_URL.equals(typeSafeConfig.baseUrl())) {
            throw new ConfigValidationException(createApiKeyConfigProblems(configName));
        }

        DecisionModelConfig decisionModelConfig = typeSafeConfig.decisionModel();
        var builder = TypeSafeDecisionModel.builder()
                .httpClientBuilder(new JaxRsHttpClientBuilder())
                .baseUrl(typeSafeConfig.baseUrl())
                .apiKey(typeSafeConfig.apiKey().orElse(null))
                .timeout(typeSafeConfig.timeout().orElse(Duration.ofSeconds(10)))
                .maxRetries(0)
                .logRequests(firstOrDefault(false, decisionModelConfig.logRequests(), typeSafeConfig.logRequests()))
                .logResponses(firstOrDefault(false, decisionModelConfig.logResponses(), typeSafeConfig.logResponses()))
                .modelName(decisionModelConfig.modelName());

        return new Function<>() {
            @Override
            public DecisionModel apply(SyntheticCreationalContext<DecisionModel> context) {
                builder.listeners(context.getInjectedReference(DECISION_MODEL_LISTENER_TYPE_LITERAL).stream()
                        .collect(Collectors.toList()));
                ModelBuilderCustomizer.applyCustomizers(
                        context.getInjectedReference(DECISION_MODEL_CUSTOMIZER_TYPE_LITERAL, Any.Literal.INSTANCE),
                        builder, configName);
                return builder.build();
            }
        };
    }

    private LangChain4jTypeSafeConfig.TypeSafeConfig correspondingTypeSafeConfig(String configName) {
        LangChain4jTypeSafeConfig.TypeSafeConfig typeSafeConfig;
        if (NamedConfigUtil.isDefault(configName)) {
            typeSafeConfig = runtimeConfig.getValue().defaultConfig();
        } else {
            typeSafeConfig = runtimeConfig.getValue().namedConfig().get(configName);
        }
        return typeSafeConfig;
    }

    private static ConfigValidationException.Problem[] createApiKeyConfigProblems(String configName) {
        return new ConfigValidationException.Problem[] { new ConfigValidationException.Problem(String.format(
                "SRCFG00014: The config property quarkus.langchain4j.typesafe%s%s is required but it could not be found in any config source",
                NamedConfigUtil.isDefault(configName) ? "." : ("." + configName + "."), "api-key")) };
    }
}
