package io.quarkiverse.langchain4j.watsonx.runtime.client;

public final class QuarkusRestClientConfig {
    private static final ThreadLocal<Boolean> LOG_CURL = ThreadLocal.withInitial(() -> false);

    private QuarkusRestClientConfig() {
    }

    public static void setLogCurl(boolean value) {
        LOG_CURL.set(value);
    }

    public static boolean isLogCurl() {
        return LOG_CURL.get();
    }

    public static void clear() {
        LOG_CURL.remove();
    }
}