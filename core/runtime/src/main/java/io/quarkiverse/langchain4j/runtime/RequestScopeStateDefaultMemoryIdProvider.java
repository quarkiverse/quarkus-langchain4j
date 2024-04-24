package io.quarkiverse.langchain4j.runtime;

import io.quarkiverse.langchain4j.spi.DefaultMemoryIdProvider;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;
import io.quarkus.arc.ManagedContext;

/**
 * This implementation uses the state of the request scope as the default value
 */
public class RequestScopeStateDefaultMemoryIdProvider implements DefaultMemoryIdProvider {

    @Override
    public int priority() {
        return DefaultMemoryIdProvider.DEFAULT_PRIORITY + 100;
    }

    @Override
    public Object getMemoryId() {
        ArcContainer container = Arc.container();
        if (container == null) {
            return null;
        }
        ManagedContext requestContext = container.requestContext();
        if (requestContext.isActive()) {
            return requestContext.getState();
        }
        return null;
    }
}
