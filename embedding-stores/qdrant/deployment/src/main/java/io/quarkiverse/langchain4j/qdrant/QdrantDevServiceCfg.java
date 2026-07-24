package io.quarkiverse.langchain4j.qdrant;

import java.util.Map;
import java.util.OptionalInt;

/**
 * @param namedStoreCollectionNames maps store name to resolved collection name for all named stores.
 *        This replaces the previous {@code Set<String>} of store names so that the record's
 *        {@code equals()} detects collection-name renames during hot-reload, triggering a
 *        container restart. A simpler {@code Set<String>} of keys would suffice for host/port
 *        propagation but would miss renames, leaving stale collections in the running container.
 */
record QdrantDevServiceCfg(
        boolean devServicesEnabled,
        OptionalInt fixedPort,
        String imageName,
        String serviceName,
        boolean shared,
        QdrantVectorCfg vectorCfg,
        boolean createCollections,
        Map<String, String> namedStoreCollectionNames) {
}
