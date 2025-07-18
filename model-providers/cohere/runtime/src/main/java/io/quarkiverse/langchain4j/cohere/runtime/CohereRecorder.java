package io.quarkiverse.langchain4j.cohere.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import dev.langchain4j.model.scoring.ScoringModel;
import io.quarkiverse.langchain4j.cohere.runtime.config.Langchain4jCohereConfig;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import io.smallrye.config.ConfigValidationException;

@Recorder
public class CohereRecorder {

    private static final String DUMMY_API_KEY = "dummy";
    private static final ConfigValidationException.Problem[] EMPTY_PROBLEMS = new ConfigValidationException.Problem[0];

    private final RuntimeValue<Langchain4jCohereConfig> runtimeConfig;

    public CohereRecorder(RuntimeValue<Langchain4jCohereConfig> runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
    }

    public Supplier<ScoringModel> cohereScoringModelSupplier(String configName) {
        Langchain4jCohereConfig.CohereConfig cohereConfig = correspondingCohereConfig(configName);

        var configProblems = checkConfigurations(cohereConfig, configName);

        if (!configProblems.isEmpty()) {
            throw new ConfigValidationException(configProblems.toArray(EMPTY_PROBLEMS));
        }

        return new Supplier<>() {
            @Override
            public ScoringModel get() {
                return new QuarkusCohereScoringModel(
                        cohereConfig.baseUrl(),
                        cohereConfig.apiKey(),
                        cohereConfig.scoringModel().modelName(),
                        cohereConfig.scoringModel().timeout(),
                        cohereConfig.scoringModel().maxRetries());
            }
        };
    }

    private List<ConfigValidationException.Problem> checkConfigurations(Langchain4jCohereConfig.CohereConfig cohereConfig,
            String configName) {
        List<ConfigValidationException.Problem> configProblems = new ArrayList<>();

        String apiKey = cohereConfig.apiKey();
        if (DUMMY_API_KEY.equals(apiKey)) {
            configProblems.add(createApiKeyConfigProblem(configName));
        }

        return configProblems;
    }

    private ConfigValidationException.Problem createApiKeyConfigProblem(String configName) {
        return createConfigProblem("api-key", configName);
    }

    private static ConfigValidationException.Problem createConfigProblem(String key, String configName) {
        return new ConfigValidationException.Problem(String.format(
                "SRCFG00014: The config property quarkus.langchain4j.cohere%s%s is required but it could not be found in any config source",
                NamedConfigUtil.isDefault(configName) ? "." : ("." + configName + "."), key));
    }

    private Langchain4jCohereConfig.CohereConfig correspondingCohereConfig(String configName) {
        Langchain4jCohereConfig.CohereConfig cohereConfig;
        if (NamedConfigUtil.isDefault(configName)) {
            cohereConfig = runtimeConfig.getValue().defaultConfig();
        } else {
            cohereConfig = runtimeConfig.getValue().namedConfig().get(configName);
        }
        return cohereConfig;
    }
}
