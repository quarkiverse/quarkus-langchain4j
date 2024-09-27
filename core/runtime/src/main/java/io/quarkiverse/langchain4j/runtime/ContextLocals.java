package io.quarkiverse.langchain4j.runtime;

import io.smallrye.common.constraint.Assert;
import io.smallrye.common.vertx.VertxContext;
import io.vertx.core.Context;
import io.vertx.core.Vertx;

public final class ContextLocals {

    private ContextLocals() {
    }

    /**
     * Gets the value from the context local associated with the given key.
     * If there is no associated value or there is no duplicated context, it returns {@code null}
     *
     * @param key the key, must not be {@code null}
     */
    public static <T> T get(String key) {
        Context current = duplicatedContextOrNull();
        if (current == null) {
            return null;
        }
        return current.getLocal(Assert.checkNotNullParam("key", key));
    }

    /**
     * Stores the given key/value in the context local.
     * This method overwrite the existing value if any.
     * If there is no duplicated context, the method does nothing
     *
     * @param key the key, must not be {@code null}
     * @param value the value, must not be {@code null}
     * @param <T> the expected type of the associated value
     */
    public static <T> void put(String key, T value) {
        Context current = duplicatedContextOrNull();
        if (current == null) {
            return;
        }
        current.putLocal(
                Assert.checkNotNullParam("key", key),
                Assert.checkNotNullParam("value", value));
    }

    /**
     * Removes the value associated with the given key from the context locals.
     * If there is no associated value or there is no duplicated context, it returns {@code false}
     *
     * @param key the key, must not be {@code null}
     * @return {@code true} if there were a value associated with the given key. {@code false} otherwise.
     */
    public static boolean remove(String key) {
        Context current = duplicatedContextOrNull();
        if (current == null) {
            return false;
        }
        return current.removeLocal(Assert.checkNotNullParam("key", key));
    }

    public static boolean duplicatedContextActive() {
        return duplicatedContextOrNull() != null;
    }

    private static Context duplicatedContextOrNull() {
        Context current = Vertx.currentContext();
        if (current == null || !VertxContext.isDuplicatedContext(current)) {
            return null;
        }
        return current;
    }
}
