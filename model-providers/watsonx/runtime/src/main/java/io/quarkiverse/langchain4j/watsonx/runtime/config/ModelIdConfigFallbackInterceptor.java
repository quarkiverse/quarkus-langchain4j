package io.quarkiverse.langchain4j.watsonx.runtime.config;

import java.util.function.Function;

import io.smallrye.config.FallbackConfigSourceInterceptor;

public class ModelIdConfigFallbackInterceptor extends FallbackConfigSourceInterceptor {

    private static final Function<String, String> FALLBACK = new Function<>() {
        @Override
        public String apply(String name) {
            if (!name.startsWith("quarkus.langchain4j.watsonx")) {
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
    };

    public ModelIdConfigFallbackInterceptor() {
        super(FALLBACK);
    }

}
