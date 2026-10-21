package io.quarkiverse.langchain4j.gemini.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Collection;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;

class ApplicationDefaultAuthProviderTest {

    @Test
    void accessTokenIsReusedAcrossRequests() {
        AtomicInteger tokenFetches = new AtomicInteger();
        var provider = new ModelAuthProviderFilter.ApplicationDefaultAuthProvider() {
            @Override
            GoogleCredentials applicationDefaultCredentials() {
                return new ServiceAccountLikeCredentials(tokenFetches);
            }
        };

        assertEquals("Bearer token-1", provider.getAuthorization(null));
        assertEquals("Bearer token-1", provider.getAuthorization(null));
        assertEquals(1, tokenFetches.get());
    }

    /**
     * Like service account and Compute Engine credentials, {@code createScoped} returns a copy without an access token.
     */
    private static final class ServiceAccountLikeCredentials extends GoogleCredentials {

        private final AtomicInteger tokenFetches;

        private ServiceAccountLikeCredentials(AtomicInteger tokenFetches) {
            this.tokenFetches = tokenFetches;
        }

        @Override
        public GoogleCredentials createScoped(Collection<String> scopes) {
            return new ServiceAccountLikeCredentials(tokenFetches);
        }

        @Override
        public AccessToken refreshAccessToken() {
            return new AccessToken("token-" + tokenFetches.incrementAndGet(),
                    new Date(System.currentTimeMillis() + 3_600_000));
        }
    }
}
