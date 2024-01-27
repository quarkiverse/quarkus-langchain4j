package io.quarkiverse.langchain4j.runtime;

import java.util.Optional;

public class OptionalUtil {

    @SafeVarargs
    public static <T> T firstOrDefault(T defaultValue, Optional<T>... values) {
        for (Optional<T> o : values) {
            if (o != null && o.isPresent()) {
                return o.get();
            }
        }
        return defaultValue;
    }
}
