package io.quarkiverse.langchain4j.auth;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.CDI;

import io.quarkiverse.langchain4j.ModelName;
import io.smallrye.mutiny.Uni;

/**
 * Model authentication providers can be used to supply credentials such as access tokens, API keys, and other type of
 * credentials.
 *
 * Providers which support a specific named model only must be annotated with a {@link ModelName} annotation.
 */
public interface ModelAuthProvider {

    /**
     * Provide authorization data which will be set as an HTTP Authorization header value.
     *
     * @param input representation of an HTTP request to the model provider.
     * @return authorization data which must include an HTTP Authorization scheme value, for example: "Bearer the_access_token".
     *         Returning null will result in no Authorization header being set.
     */
    String getAuthorization(Input input);

    default Uni<String> getAuthorizationAsync(Input input) {
        return Uni.createFrom().item(getAuthorization(input));
    }

    /*
     * Representation of an HTTP request to the model provider
     */
    interface Input {
        /*
         * HTTP request method, such as POST or GET
         */
        String method();

        /*
         * HTTP request URI
         */
        URI uri();

        /*
         * HTTP request headers
         */
        Map<String, List<Object>> headers();
    }

    /**
     * Resolve ModelAuthProvider.
     *
     * @param modelName the model name. If the model name is not null then a ModelAuthProvider with a matching {@link ModelName}
     *        annotation are preferred to a global ModelAuthProvider.
     * @return Resolved ModelAuthProvider as an Optional value which will be empty if no ModelAuthProvider is available.
     */
    static Optional<ModelAuthProvider> resolve(String modelName) {
        ModelAuthProvider authorizer = null;
        // If a model is named then try to find ModelAuthProvider matching this model only
        if (modelName != null) {
            Instance<ModelAuthProvider> beanInstance = CDI.current().select(ModelAuthProvider.class,
                    ModelName.Literal.of(modelName));

            for (var handle : beanInstance.handles()) {
                authorizer = handle.get();
                break;
            }
        }
        // Find a generic ModelAuthProvider if no model specific ModelAuthProvider is available
        if (authorizer == null) {
            Instance<ModelAuthProvider> beanInstance = CDI.current().select(ModelAuthProvider.class);
            for (var handle : beanInstance.handles()) {
                authorizer = handle.get();
                break;
            }
        }
        return Optional.ofNullable(authorizer);
    }
}
