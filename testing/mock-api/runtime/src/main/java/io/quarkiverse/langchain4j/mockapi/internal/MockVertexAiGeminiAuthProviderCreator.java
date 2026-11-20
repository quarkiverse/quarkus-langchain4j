package io.quarkiverse.langchain4j.mockapi.internal;

import io.quarkus.arc.BeanCreator;
import io.quarkus.arc.SyntheticCreationalContext;

/**
 * Creates the {@link MockVertexAiGeminiAuthProvider} synthetic beans. Typed {@code Object} so that the deployment module
 * can reference it without the optional core dependency.
 */
public class MockVertexAiGeminiAuthProviderCreator implements BeanCreator<Object> {

    @Override
    public Object create(SyntheticCreationalContext<Object> context) {
        return new MockVertexAiGeminiAuthProvider();
    }
}
