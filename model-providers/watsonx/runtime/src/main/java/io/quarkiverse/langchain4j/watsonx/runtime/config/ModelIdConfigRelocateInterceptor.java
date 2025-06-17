package io.quarkiverse.langchain4j.watsonx.runtime.config;

import java.util.function.Function;

import io.smallrye.config.RelocateConfigSourceInterceptor;

public class ModelIdConfigRelocateInterceptor extends RelocateConfigSourceInterceptor {

    private static final Function<String, String> RELOCATE = new Function<>() {
        @Override
        public String apply(String name) {
            if (!name.startsWith("quarkus.langchain4j.watsonx")) {
                return name;
            }
            if (!name.endsWith("model-name")) {
                return name;
            }
            int index = name.lastIndexOf(".model-name");
            if (index < 1) {
                return name;
            }
            return name.substring(0, index) + ".model-id";
        }
    };

    public ModelIdConfigRelocateInterceptor() {
        super(RELOCATE);
    }
}
