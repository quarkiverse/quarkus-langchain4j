package io.quarkiverse.langchain4j.qdrant;

import io.qdrant.client.grpc.Collections;

public record QdrantVectorCfg(
        Collections.Distance distance,
        long size) {
}
