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
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

public class WorkloadModelAuthProvider implements ModelAuthProvider {

    private static final Logger LOG = Logger.getLogger(WorkloadModelAuthProvider.class);

    private final Vertx vertx;
    private final OidcClient oidcClient;
    private final TokensHelper tokensHelper = new TokensHelper();
    private final Path tokenPath;
    private final String tokenParamName;
    private volatile String identityToken;
    private volatile long timerId = -1;

    WorkloadModelAuthProvider(Vertx vertx, OidcClient oidcClient, String tokenPath, String tokenParamName) {
        this.vertx = vertx;
        this.oidcClient = oidcClient;
        this.tokenPath = Path.of(tokenPath);
        this.tokenParamName = tokenParamName;
        this.identityToken = loadFromFileSystem();
    }

    @Override
    public String getAuthorization(Input input) {
        String token = identityToken;
        if (token == null) {
            token = reloadIdentityToken();
        }
        if (token == null) {
            return null;
        }
        return "Bearer " + tokensHelper.getTokens(oidcClient, Map.of(tokenParamName, token), false)
                .await().indefinitely().getAccessToken();
    }

    private synchronized String reloadIdentityToken() {
        if (identityToken == null) {
            identityToken = loadFromFileSystem();
        }
        return identityToken;
    }

    private String loadFromFileSystem() {
        if (!Files.exists(tokenPath)) {
            LOG.warnf("Workload identity token file not found: %s", tokenPath);
            return null;
        }
        try {
            String token = Files.readString(tokenPath).trim();
            if (token.isEmpty()) {
                LOG.errorf("Workload identity token file is empty: %s", tokenPath);
                return null;
            }
            Long expiresAt = getExpiresAt(token);
            if (expiresAt != null) {
                scheduleRefresh(expiresAt);
            }
            return token;
        } catch (IOException e) {
            LOG.errorf(e, "Failed to read workload identity token file: %s", tokenPath);
            return null;
        }
    }

    private void scheduleRefresh(long expiresAt) {
        cancelRefresh();
        long nowSecs = System.currentTimeMillis() / 1000;
        long ttlMs = (expiresAt - nowSecs) * 1000;
        // refresh at 85% of TTL, matching Kubernetes token rotation conventions
        long delayMs = (long) (ttlMs * 0.85);
        if (delayMs > 0) {
            timerId = vertx.setTimer(delayMs, id -> {
                identityToken = loadFromFileSystem();
            });
        }
    }

    private void cancelRefresh() {
        if (timerId >= 0) {
            vertx.cancelTimer(timerId);
            timerId = -1;
        }
    }

    static Long getExpiresAt(String jwt) {
        String[] parts = jwt.split("\\.");
        if (parts.length != 3) {
            return null;
        }
        try {
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            JsonObject claims = new JsonObject(payload);
            return claims.containsKey("exp") ? claims.getLong("exp") : null;
        } catch (Exception e) {
            LOG.debugf("Failed to decode JWT expiry claim: %s", e.getMessage());
            return null;
        }
    }
}
