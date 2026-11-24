package io.quarkiverse.langchain4j.watsonx.runtime.client;

import java.util.concurrent.CompletableFuture;

import com.ibm.watsonx.ai.core.auth.Authenticator;

public final class QuarkusRestClientConfig {
    private static final ThreadLocal<Boolean> LOG_CURL = new ThreadLocal<>();

    private QuarkusRestClientConfig() {
    }

    public static void setLogCurl(boolean value) {
        LOG_CURL.set(value);
    }

    public static boolean isLogCurl() {
        return Boolean.TRUE.equals(LOG_CURL.get());
    }

    public static boolean isLogCurl(Authenticator authenticator) {
        if (authenticator instanceof ClientAuthenticator configured) {
            return configured.logCurl;
        }
        return isLogCurl();
    }

    /**
     * Keeps the service's logging option with the authenticator that the SDK propagates to lazily created storage clients.
     */
    public static Authenticator withLogCurl(Authenticator authenticator) {
        return new ClientAuthenticator(authenticator, isLogCurl());
    }

    public static void clear() {
        LOG_CURL.remove();
    }

    private static final class ClientAuthenticator implements Authenticator {
        private final Authenticator delegate;
        private final boolean logCurl;

        private ClientAuthenticator(Authenticator delegate, boolean logCurl) {
            this.delegate = delegate;
            this.logCurl = logCurl;
        }

        @Override
        public String token() {
            return delegate.token();
        }

        @Override
        public CompletableFuture<String> tokenAsync() {
            return delegate.tokenAsync();
        }

        @Override
        public String scheme() {
            return delegate.scheme();
        }
    }
}
