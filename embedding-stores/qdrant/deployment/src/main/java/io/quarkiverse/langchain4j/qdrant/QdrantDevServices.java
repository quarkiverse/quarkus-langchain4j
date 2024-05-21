package io.quarkiverse.langchain4j.qdrant;

import io.quarkus.devservices.common.ContainerLocator;

public final class QdrantDevServices {
    /**
     * Label to add to shared Dev Service for Chroma running in containers.
     * This allows other applications to discover the running service and use it instead of starting a new instance.
     */
    static final String DEV_SERVICE_LABEL = "quarkus-dev-service-qdrant";
    static final int QDRANT_PORT = 6334;

    static final ContainerLocator LOCATOR = new ContainerLocator(DEV_SERVICE_LABEL, QDRANT_PORT);

    private QdrantDevServices() {
    }
}
