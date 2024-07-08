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
        // This will likely need to be refactored again.
        // ModelAuthProvider should return a set of supported models (empty by default),
        // otherwise the resolution on the main branch does not work for more than one OIDC provider
        ModelAuthProvider authorizer = null;
        if (modelName != null) {
            Instance<ModelAuthProvider> beanInstance = CDI.current().select(ModelAuthProvider.class,
                    ModelName.Literal.of(modelName));

            for (var handle : beanInstance.handles()) {
                authorizer = handle.get();
                break;
            }
        }
        if (authorizer == null) {
            Instance<ModelAuthProvider> beanInstance = CDI.current().select(ModelAuthProvider.class);
            for (var handle : beanInstance.handles()) {
                authorizer = handle.get();
                break;
            }
        }
        return Optional.ofNullable(authorizer);
    }

    static Optional<ModelAuthProvider> resolve() {
        return resolve(null);
    }

}
