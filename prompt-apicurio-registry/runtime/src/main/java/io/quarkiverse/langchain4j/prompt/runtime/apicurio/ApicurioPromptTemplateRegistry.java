package io.quarkiverse.langchain4j.prompt.runtime.apicurio;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jboss.logging.Logger;

import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.ProblemDetails;
import io.apicurio.registry.rest.client.models.VersionMetaData;
import io.quarkiverse.langchain4j.prompt.PromptTemplateReference;
import io.quarkiverse.langchain4j.prompt.PromptTemplateRegistry;
import io.quarkiverse.langchain4j.prompt.PromptTemplateResolutionException;
import io.quarkiverse.langchain4j.prompt.ResolvedPromptTemplate;

/**
 * A {@link PromptTemplateRegistry} backed by Apicurio Registry {@code PROMPT_TEMPLATE} artifacts.
 * <p>
 * Resolved templates are cached. Once the configured TTL expires, the cached template keeps being served while a
 * refresh is performed in the background (stale-while-revalidate), so that AI service invocations are not blocked by
 * the registry once a template has been resolved. If a refresh fails, the previously resolved template is kept and a
 * warning is logged.
 * <p>
 * This class is NOT a CDI bean by default. It is registered as a synthetic bean by the deployment processor when the
 * integration is enabled. It can also be created programmatically via {@link ApicurioPromptTemplateRegistryBuilder}.
 */
public class ApicurioPromptTemplateRegistry implements PromptTemplateRegistry {

    private static final Logger log = Logger.getLogger(ApicurioPromptTemplateRegistry.class);

    static final String PROMPT_TEMPLATE_ARTIFACT_TYPE = "PROMPT_TEMPLATE";
    static final String LATEST_ALIAS = "latest";
    static final String LATEST_BRANCH_EXPRESSION = "branch=latest";

    private final RegistryClient registryClient;
    private final String defaultGroup;
    private final String defaultVersion;
    private final long ttlNanos;
    private final Executor refreshExecutor;
    private final Map<PromptTemplateReference, CacheEntry> cache = new ConcurrentHashMap<>();
    private final Map<PromptTemplateReference, Object> loadLocks = new ConcurrentHashMap<>();

    public ApicurioPromptTemplateRegistry(RegistryClient registryClient, String defaultGroup, String defaultVersion,
            Duration cacheTtl, Executor refreshExecutor) {
        this.registryClient = Objects.requireNonNull(registryClient, "registryClient is required");
        this.defaultGroup = defaultGroup == null || defaultGroup.isBlank() ? "default" : defaultGroup;
        this.defaultVersion = defaultVersion == null || defaultVersion.isBlank() ? LATEST_BRANCH_EXPRESSION : defaultVersion;
        this.ttlNanos = cacheTtl == null || cacheTtl.isZero() || cacheTtl.isNegative() ? -1 : cacheTtl.toNanos();
        this.refreshExecutor = Objects.requireNonNull(refreshExecutor, "refreshExecutor is required");
    }

    @Override
    public ResolvedPromptTemplate resolve(PromptTemplateReference reference) {
        PromptTemplateReference key = normalize(reference);
        CacheEntry cached = cache.get(key);
        if (cached != null) {
            if (cached.isExpired() && cached.refreshing.compareAndSet(false, true)) {
                refreshExecutor.execute(() -> refresh(key, cached));
            }
            return cached.template;
        }
        Object lock = loadLocks.computeIfAbsent(key, k -> new Object());
        synchronized (lock) {
            CacheEntry entry = cache.get(key);
            if (entry == null) {
                entry = new CacheEntry(fetch(key), expiration());
                cache.put(key, entry);
            }
            return entry.template;
        }
    }

    /**
     * Removes every cached template, forcing a fetch from the registry on next use.
     */
    public void invalidate() {
        cache.clear();
    }

    /**
     * Removes the given template from the cache, forcing a fetch from the registry on next use.
     */
    public void invalidate(PromptTemplateReference reference) {
        cache.remove(normalize(reference));
    }

    /**
     * Fetches the given template from the registry, bypassing and updating the cache.
     */
    public ResolvedPromptTemplate refresh(PromptTemplateReference reference) {
        PromptTemplateReference key = normalize(reference);
        CacheEntry entry = new CacheEntry(fetch(key), expiration());
        cache.put(key, entry);
        return entry.template;
    }

    /**
     * @return the reference with the default group and version applied, and the {@code latest} alias expanded
     */
    public PromptTemplateReference normalize(PromptTemplateReference reference) {
        PromptTemplateReference normalized = reference.withDefaults(defaultGroup, defaultVersion);
        if (LATEST_ALIAS.equalsIgnoreCase(normalized.version())) {
            normalized = new PromptTemplateReference(normalized.groupId(), normalized.artifactId(),
                    LATEST_BRANCH_EXPRESSION);
        }
        return normalized;
    }

    private void refresh(PromptTemplateReference key, CacheEntry stale) {
        try {
            ResolvedPromptTemplate refreshed = fetch(key);
            cache.put(key, new CacheEntry(refreshed, expiration()));
        } catch (RuntimeException e) {
            log.warnf(e, "Failed to refresh prompt template '%s' from Apicurio Registry, keeping the cached version %s",
                    key, stale.template.version());
            stale.extend(expiration());
        } finally {
            stale.refreshing.set(false);
        }
    }

    private long expiration() {
        return ttlNanos < 0 ? Long.MAX_VALUE : System.nanoTime() + ttlNanos;
    }

    private ResolvedPromptTemplate fetch(PromptTemplateReference reference) {
        VersionMetaData metadata;
        String content;
        try {
            var versionRequest = registryClient.groups().byGroupId(reference.groupId())
                    .artifacts().byArtifactId(reference.artifactId())
                    .versions().byVersionExpression(reference.version());
            metadata = versionRequest.get();
            try (InputStream is = versionRequest.content().get()) {
                content = is == null ? "" : new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (ProblemDetails e) {
            throw new PromptTemplateResolutionException("Failed to fetch prompt template '" + reference
                    + "' from Apicurio Registry: " + describe(e), e);
        } catch (IOException | RuntimeException e) {
            throw new PromptTemplateResolutionException("Failed to fetch prompt template '" + reference
                    + "' from Apicurio Registry: " + e.getMessage(), e);
        }

        String version = metadata != null ? metadata.getVersion() : null;
        if (metadata != null && metadata.getArtifactType() != null
                && !PROMPT_TEMPLATE_ARTIFACT_TYPE.equals(metadata.getArtifactType())) {
            log.warnf("Artifact '%s' in Apicurio Registry has type %s instead of %s; its content is used as a prompt "
                    + "template but the registry will not validate it or enforce compatibility rules for prompts",
                    reference, metadata.getArtifactType(), PROMPT_TEMPLATE_ARTIFACT_TYPE);
        }
        ResolvedPromptTemplate template = PromptTemplateContentParser.parse(content, version);
        log.debugf("Resolved prompt template '%s' (version %s) from Apicurio Registry", reference, template.version());
        return template;
    }

    private static String describe(ProblemDetails problem) {
        StringBuilder sb = new StringBuilder();
        if (problem.getStatus() != null) {
            sb.append("HTTP ").append(problem.getStatus()).append(' ');
        }
        if (problem.getTitle() != null) {
            sb.append(problem.getTitle());
        } else if (problem.getMessage() != null) {
            sb.append(problem.getMessage());
        }
        if (problem.getDetail() != null) {
            sb.append(" - ").append(problem.getDetail());
        }
        return sb.toString().strip();
    }

    private static final class CacheEntry {
        private final ResolvedPromptTemplate template;
        private volatile long expiresAtNanos;
        private final AtomicBoolean refreshing = new AtomicBoolean();

        private CacheEntry(ResolvedPromptTemplate template, long expiresAtNanos) {
            this.template = template;
            this.expiresAtNanos = expiresAtNanos;
        }

        private boolean isExpired() {
            return expiresAtNanos != Long.MAX_VALUE && System.nanoTime() - expiresAtNanos >= 0;
        }

        private void extend(long expiresAtNanos) {
            this.expiresAtNanos = expiresAtNanos;
        }
    }
}
