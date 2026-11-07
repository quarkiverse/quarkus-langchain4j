package io.quarkiverse.langchain4j.llama3.runtime;

import java.util.HashMap;
import java.util.Map;

import io.quarkiverse.langchain4j.llama3.copy.AOT;
import io.quarkus.runtime.annotations.Recorder;
import io.quarkus.runtime.annotations.StaticInit;

@Recorder
public class Llama3PreloadRecorder {

    public static final Map<NameAndQuantization, AOT.PartialModel> PRELOADED_MODELS = new HashMap<>();

    @StaticInit
    public void preloadModel(String modelName, String modelQuantization, String modelFullPathOnBuildMachine) {
        PRELOADED_MODELS.put(new NameAndQuantization(modelName, modelQuantization),
                AOT.preLoadGGUF(modelFullPathOnBuildMachine));
    }

    public static AOT.PartialModel getPreloadModel(String modelName, String modelQuantization) {
        return PRELOADED_MODELS.get(new NameAndQuantization(modelName, modelQuantization));
    }
}
