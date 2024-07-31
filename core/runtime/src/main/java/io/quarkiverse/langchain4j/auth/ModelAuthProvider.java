package io.quarkiverse.langchain4j.auth;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.CDI;

import io.quarkiverse.langchain4j.ModelName;

public interface ModelAuthProvider {
    String getAuthorization(Input input);

    interface Input {
        String method();

        URI uri();

        Map<String, List<Object>> headers();
    }

    static Optional<ModelAuthProvider> resolve(String modelName) {
        Instance<ModelAuthProvider> beanInstance = modelName == null
                ? CDI.current().select(ModelAuthProvider.class)
                : CDI.current().select(ModelAuthProvider.class, ModelName.Literal.of(modelName));

        //get the first one without causing a bean1 resolution exception
        ModelAuthProvider authorizer = null;
        for (var handle : beanInstance.handles()) {
            authorizer = handle.get();
            break;
        }
        return Optional.ofNullable(authorizer);
    }

    static Optional<ModelAuthProvider> resolve() {
        return resolve(null);
    }

}
