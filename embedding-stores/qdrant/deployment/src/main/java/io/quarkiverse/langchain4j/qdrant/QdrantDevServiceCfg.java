package io.quarkiverse.langchain4j.qdrant;

import java.util.OptionalInt;

record QdrantDevServiceCfg(
        boolean devServicesEnabled,
        OptionalInt fixedPort,
        String imageName,
        String serviceName,
        boolean shared,
        QdrantVectorCfg vectorCfg) {
}