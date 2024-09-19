package io.quarkiverse.langchain4j.ollama.runtime.config;

import java.util.function.Function;

final class ModelIdToModelNameFunction implements Function<String, String> {

    static final ModelIdToModelNameFunction INSTANCE = new ModelIdToModelNameFunction();

    private ModelIdToModelNameFunction() {
    }

    @Override
    public String apply(String name) {
        if (!name.startsWith("quarkus.langchain4j.ollama")) {
            return name;
        }
        if (!name.endsWith("model-id")) {
            return name;
        }
        int index = name.lastIndexOf(".model-id");
        if (index < 1) {
            return name;
        }
        return name.substring(0, index) + ".model-name";
    }
}
