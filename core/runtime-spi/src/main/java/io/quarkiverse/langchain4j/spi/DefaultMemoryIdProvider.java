package io.quarkiverse.langchain4j.spi;

/**
 * Quarkus extension can decide whether they can provide a default value for the memory ID object if none was explicitly
 * provided.
 * <p>
 * The idea behind this is that depending on the type of request that is being served, Quarkus can determine a unique
 * (per request) object to be used.
 */
public interface DefaultMemoryIdProvider {
    int DEFAULT_PRIORITY = 0;

    /**
     * Defines the priority of the providers.
     * A lower integer value means that the customizer will be considered before one with a higher priority
     */
    default int priority() {
        return DEFAULT_PRIORITY;
    }

    /**
     * Determines the object to be used as the default memory ID.
     * A value of {@code null} means that the provider is not going to give a value and therefore he next
     * provider should be tried.
     */
    Object getMemoryId();
}
