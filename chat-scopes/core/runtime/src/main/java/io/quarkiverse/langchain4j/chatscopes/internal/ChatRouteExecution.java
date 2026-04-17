package io.quarkiverse.langchain4j.chatscopes.internal;

import io.quarkus.arc.Arc;
import io.smallrye.mutiny.Uni;

public interface ChatRouteExecution {

    Uni<Void> execute();

    default Class<?> getBeanClass() {
        return null;
    }

    default <T> T getBean() {
        if (getBeanClass() == null) {
            return null;
        }
        return (T) Arc.container().instance(getBeanClass()).get();
    }

}
