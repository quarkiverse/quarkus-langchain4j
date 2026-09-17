package io.quarkiverse.langchain4j.prompt.runtime.apicurio;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.common.HttpAdapterType;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.rest.client.RegistryClient;

/**
 * Builder for creating {@link ApicurioPromptTemplateRegistry} instances programmatically, for use cases where CDI is
 * not available or a registry different from the configured one must be used.
 */
public class ApicurioPromptTemplateRegistryBuilder {

    private String registryUrl;
    private RegistryClient registryClient;
    private String username;
    private String password;
    private String tokenEndpoint;
    private String clientId;
    private String clientSecret;
    private String scope;
    private String defaultGroup = "default";
    private String defaultVersion = ApicurioPromptTemplateRegistry.LATEST_BRANCH_EXPRESSION;
    private Duration cacheTtl = Duration.ofSeconds(60);
    private Executor refreshExecutor;

    public static ApicurioPromptTemplateRegistryBuilder create() {
        return new ApicurioPromptTemplateRegistryBuilder();
    }

    public ApicurioPromptTemplateRegistryBuilder registryUrl(String registryUrl) {
        this.registryUrl = registryUrl;
        return this;
    }

    /**
     * Uses an existing client instead of creating one from {@link #registryUrl(String)} and the authentication settings.
     */
    public ApicurioPromptTemplateRegistryBuilder registryClient(RegistryClient registryClient) {
        this.registryClient = registryClient;
        return this;
    }

    public ApicurioPromptTemplateRegistryBuilder basicAuth(String username, String password) {
        this.username = username;
        this.password = password;
        return this;
    }

    public ApicurioPromptTemplateRegistryBuilder oauth2(String tokenEndpoint, String clientId, String clientSecret) {
        return oauth2(tokenEndpoint, clientId, clientSecret, null);
    }

    public ApicurioPromptTemplateRegistryBuilder oauth2(String tokenEndpoint, String clientId, String clientSecret,
            String scope) {
        this.tokenEndpoint = tokenEndpoint;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.scope = scope;
        return this;
    }

    public ApicurioPromptTemplateRegistryBuilder defaultGroup(String defaultGroup) {
        this.defaultGroup = defaultGroup;
        return this;
    }

    public ApicurioPromptTemplateRegistryBuilder defaultVersion(String defaultVersion) {
        this.defaultVersion = defaultVersion;
        return this;
    }

    /**
     * @param cacheTtl how long a resolved template is considered fresh; zero or negative disables refreshing
     */
    public ApicurioPromptTemplateRegistryBuilder cacheTtl(Duration cacheTtl) {
        this.cacheTtl = cacheTtl;
        return this;
    }

    /**
     * @param refreshExecutor the executor used to refresh expired templates in the background
     */
    public ApicurioPromptTemplateRegistryBuilder refreshExecutor(Executor refreshExecutor) {
        this.refreshExecutor = refreshExecutor;
        return this;
    }

    public ApicurioPromptTemplateRegistry build() {
        RegistryClient client = registryClient;
        if (client == null) {
            Objects.requireNonNull(registryUrl, "registryUrl is required");
            client = createClient(registryUrl, username, password, tokenEndpoint, clientId, clientSecret, scope);
        }
        Executor executor = refreshExecutor != null ? refreshExecutor : ForkJoinPool.commonPool();
        return new ApicurioPromptTemplateRegistry(client, defaultGroup, defaultVersion, cacheTtl, executor);
    }

    static RegistryClient createClient(String registryUrl, String username, String password, String tokenEndpoint,
            String clientId, String clientSecret, String scope) {
        RegistryClientOptions options = RegistryClientOptions.create(registryUrl).httpAdapter(HttpAdapterType.JDK);
        if (username != null && password != null) {
            options.basicAuth(username, password);
        } else if (tokenEndpoint != null && clientId != null && clientSecret != null) {
            if (scope != null) {
                options.oauth2(tokenEndpoint, clientId, clientSecret, scope);
            } else {
                options.oauth2(tokenEndpoint, clientId, clientSecret);
            }
        }
        return RegistryClientFactory.create(options);
    }
}
