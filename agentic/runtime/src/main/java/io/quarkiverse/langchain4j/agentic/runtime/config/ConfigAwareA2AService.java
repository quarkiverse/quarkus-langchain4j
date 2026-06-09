package io.quarkiverse.langchain4j.agentic.runtime.config;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

import dev.langchain4j.agentic.internal.A2AClientBuilder;
import dev.langchain4j.agentic.internal.A2AService;
import dev.langchain4j.agentic.internal.AgentExecutor;
import dev.langchain4j.agentic.internal.InternalAgent;
import io.quarkiverse.langchain4j.agentic.runtime.AgenticRuntimeConfig;

/**
 * TEMPORARY WORKAROUND — will be removed when upstream provides
 * workflow-level AgentConfigurator (see langchain4j/langchain4j#5399).
 * Reflection on A2AService.Provider.a2aService will be removed when
 * upstream adds A2AService.setA2AService() (see langchain4j/langchain4j#5400).
 */
public final class ConfigAwareA2AService implements A2AService {

    private static final Logger log = Logger.getLogger(ConfigAwareA2AService.class);
    private static final Pattern CONFIG_EXPRESSION = Pattern.compile("^\\$\\{(.+)}$");

    private final A2AService delegate;
    private final AgenticRuntimeConfig config;
    private final Map<String, String> classNameToConfigKey;

    public ConfigAwareA2AService(A2AService delegate, AgenticRuntimeConfig config,
            Map<String, String> classNameToConfigKey) {
        this.delegate = delegate;
        this.config = config;
        this.classNameToConfigKey = classNameToConfigKey;
    }

    @Override
    public <T> A2AClientBuilder<T> a2aBuilder(String a2aServerUrl, Class<T> agentServiceClass) {
        String resolvedUrl = resolveUrl(a2aServerUrl, agentServiceClass);
        return delegate.a2aBuilder(resolvedUrl, agentServiceClass);
    }

    @Override
    public Optional<AgentExecutor> methodToAgentExecutor(InternalAgent agent, Method method) {
        return delegate.methodToAgentExecutor(agent, method);
    }

    private <T> String resolveUrl(String annotationUrl, Class<T> agentServiceClass) {
        // Priority 1: per-agent named config
        String configKey = classNameToConfigKey.get(agentServiceClass.getName());
        if (configKey != null) {
            var namedConfig = config.namedConfig();
            if (namedConfig != null) {
                var agentConfig = namedConfig.get(configKey);
                if (agentConfig != null && agentConfig.a2aServerUrl().isPresent()) {
                    log.debugf("A2A URL for %s resolved from config key '%s': %s",
                            agentServiceClass.getSimpleName(), configKey,
                            agentConfig.a2aServerUrl().get());
                    return agentConfig.a2aServerUrl().get();
                }
            }
        }

        // Priority 2: config expression in annotation value
        Matcher matcher = CONFIG_EXPRESSION.matcher(annotationUrl);
        if (matcher.matches()) {
            String configPropertyName = matcher.group(1);
            try {
                String resolved = ConfigProvider.getConfig()
                        .getValue(configPropertyName, String.class);
                log.debugf("A2A URL for %s resolved from expression '${%s}': %s",
                        agentServiceClass.getSimpleName(), configPropertyName, resolved);
                return resolved;
            } catch (java.util.NoSuchElementException e) {
                throw new IllegalStateException(
                        "Config expression '${" + configPropertyName
                                + "}' in @A2AClientAgent.a2aServerUrl on "
                                + agentServiceClass.getName()
                                + " could not be resolved. Define '"
                                + configPropertyName + "' in application.properties.",
                        e);
            }
        }

        // Priority 3: raw annotation value
        return annotationUrl;
    }
}
