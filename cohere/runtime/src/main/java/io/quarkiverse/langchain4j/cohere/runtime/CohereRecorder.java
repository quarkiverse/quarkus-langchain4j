package io.quarkiverse.langchain4j.cohere.runtime;

import java.util.function.Supplier;

import dev.langchain4j.model.scoring.ScoringModel;
import io.quarkiverse.langchain4j.cohere.runtime.config.CohereConfig;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class CohereRecorder {

    public Supplier<ScoringModel> cohereScoringModelSupplier(CohereConfig config) {
        return new Supplier<>() {
            @Override
            public ScoringModel get() {
                return new QuarkusCohereScoringModel(
                        config.baseUrl(),
                        config.apiKey(),
                        config.scoringModel().modelName(),
                        config.scoringModel().timeout(),
                        config.scoringModel().maxRetries());
            }
        };
    }

}
