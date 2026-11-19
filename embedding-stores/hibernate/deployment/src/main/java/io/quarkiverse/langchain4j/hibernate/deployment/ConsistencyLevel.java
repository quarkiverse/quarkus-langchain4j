package io.quarkiverse.langchain4j.hibernate.deployment;

/**
 * The consistency level to apply for queries.
 * Some databases don't block inserts/updates/deletes until indexes have been updated and allow returning somewhat
 * "stale" results for read queries, depending on the configured consistency level.
 * <p>
 * SQL databases generally always have {@code STRONG} semantics and no way to configure the consistency,
 * whereas NoSQL databases may default to {@code EVENTUALLY} or {@code BOUNDED}.
 * <p>
 * On the Milvus database, these consistency levels map to the same named Milvus consistency levels.
 */
public enum ConsistencyLevel {
    /**
     * Reads are guaranteed to see all committed data across sessions.
     */
    STRONG,
    /**
     * Reads are guaranteed to see data that the current session changed.
     */
    SESSION,
    /**
     * Reads are guaranteed to see data previously changed data up to a certain staleness.
     */
    BOUNDED,
    /**
     * Reads are not guaranteed to see previously changed data, but will do so eventually when indexing finishes.
     */
    EVENTUALLY
}
