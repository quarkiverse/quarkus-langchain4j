package io.quarkiverse.langchain4j.workload.runtime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;

import org.jboss.logging.Logger;

import io.quarkiverse.langchain4j.auth.ModelAuthProvider;
import io.quarkus.oidc.client.OidcClient;
import io.quarkus.oidc.client.runtime.TokensHelper;
import io.quarkus.spiffe.svid.jwt.JwtSvidClient;
import io.quarkus.spiffe.svid.jwt.WorkloadJsonWebToken;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

public class WorkloadModelAuthProvider implements ModelAuthProvider {

    private record IdentityToken(String token, long expiresAt, long timerId) {
        private boolean isExpired() {
            final long nowSecs = System.currentTimeMillis() / 1000;
            return nowSecs > expiresAt;
        }
    }

    private static final Logger LOG = Logger.getLogger(WorkloadModelAuthProvider.class);

    private final Vertx vertx;
    private final OidcClient oidcClient;
    private final TokensHelper tokensHelper = new TokensHelper();
    private final Path tokenPath;
    private final JwtSvidClient spiffeClient;
    private final String tokenParamName;
    private volatile IdentityToken identityToken;

    WorkloadModelAuthProvider(Vertx vertx, OidcClient oidcClient, String tokenPath, JwtSvidClient spiffeClient,
            String tokenParamName) {
        this.vertx = vertx;
        this.oidcClient = oidcClient;
        this.tokenPath = tokenPath == null ? null : Path.of(tokenPath);
        this.spiffeClient = spiffeClient;
        this.tokenParamName = tokenParamName;
        this.identityToken = loadToken();
    }

    @Override
    public String getAuthorization(Input input) {
        IdentityToken current = identityToken;
        if (isInvalid(current)) {
            current = loadIdentityToken();
        }
        if (current == null) {
            return null;
        }
        return "Bearer " + tokensHelper.getTokens(oidcClient, Map.of(tokenParamName, current.token), false)
                .await().indefinitely().getAccessToken();
    }

    private synchronized IdentityToken loadIdentityToken() {
        if (isInvalid(identityToken)) {
            cancelRefresh();
            identityToken = loadToken();
        }
        return identityToken;
    }

    private IdentityToken loadToken() {
        return tokenPath != null ? loadFromFileSystem() : loadFromSpiffeWorkloadApi();
    }

    private IdentityToken loadFromSpiffeWorkloadApi() {
        try {
            WorkloadJsonWebToken jwtSvid = spiffeClient.getWorkloadJsonWebToken().await().indefinitely();
            long expiresAt = jwtSvid.expiry().getEpochSecond();
            return new IdentityToken(jwtSvid.token(), expiresAt, scheduleRefresh(expiresAt));
        } catch (RuntimeException e) {
            LOG.errorf(e, "Failed to fetch a SPIFFE JWT-SVID from the SPIFFE Workload API: %s", e.getMessage());
            return null;
        }
    }

    private long scheduleRefresh(long expiresAt) {
        long nowSecs = System.currentTimeMillis() / 1000;
        long ttlMs = (expiresAt - nowSecs) * 1000;
        // refresh at 85% of TTL, matching Kubernetes token rotation conventions
        long delayMs = (long) (ttlMs * 0.85);
        if (delayMs <= 0) {
            return -1;
        }
        return vertx.setTimer(delayMs, new Handler<Long>() {
            @Override
            public void handle(Long ignored) {
                WorkloadModelAuthProvider.this.identityToken = loadToken();
            }
        });
    }

    private void cancelRefresh() {
        if (identityToken != null) {
            vertx.cancelTimer(identityToken.timerId);
        }
    }

    private IdentityToken loadFromFileSystem() {
        if (Files.exists(tokenPath)) {
            try {
                String token = Files.readString(tokenPath).trim();
                if (token.isEmpty()) {
                    LOG.errorf("Workload identity token file is empty: %s", tokenPath);
                    return null;
                }
                Long expiresAt = getExpiresAt(token);
                if (expiresAt != null) {
                    return new IdentityToken(token, expiresAt, scheduleRefresh(expiresAt));
                } else {
                    LOG.errorf("Workload identity token or its 'exp' claim is invalid: %s", tokenPath);
                }
            } catch (IOException e) {
                LOG.errorf(e, "Failed to read workload identity token file: %s", tokenPath);
            }
        } else {
            LOG.warnf("Workload identity token file not found: %s", tokenPath);
        }
        return null;
    }

    private static boolean isInvalid(IdentityToken identityToken) {
        return identityToken == null || identityToken.isExpired();
    }

    static Long getExpiresAt(String jwt) {
        String[] parts = jwt.split("\\.");
        if (parts.length != 3) {
            return null;
        }
        try {
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            JsonObject claims = new JsonObject(payload);
            if (!claims.containsKey("exp")) {
                return null;
            }
            return claims.getLong("exp");
        } catch (Exception e) {
            LOG.debugf("Failed to decode JWT expiry claim: %s", e.getMessage());
            return null;
        }
    }
}
