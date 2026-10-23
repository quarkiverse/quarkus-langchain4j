package io.quarkiverse.langchain4j.prompt.runtime.apicurio;

import java.util.List;
import java.util.function.Function;

import org.jboss.logging.Logger;

import io.apicurio.registry.rest.client.RegistryClient;
import io.quarkiverse.langchain4j.prompt.runtime.apicurio.config.PromptApicurioRegistryRuntimeConfig;
import io.quarkus.arc.Arc;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import io.quarkus.runtime.configuration.ConfigurationException;
import io.smallrye.mutiny.infrastructure.Infrastructure;

@Recorder
public class PromptApicurioRegistryRecorder {

    private static final Logger log = Logger.getLogger(PromptApicurioRegistryRecorder.class);

    private final RuntimeValue<PromptApicurioRegistryRuntimeConfig> runtimeConfig;

    public PromptApicurioRegistryRecorder(RuntimeValue<PromptApicurioRegistryRuntimeConfig> runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
    }

    public Function<SyntheticCreationalContext<ApicurioPromptTemplateRegistry>, ApicurioPromptTemplateRegistry> registryFunction() {
        return new Function<>() {
            @Override
            public ApicurioPromptTemplateRegistry apply(SyntheticCreationalContext<ApicurioPromptTemplateRegistry> context) {
                PromptApicurioRegistryRuntimeConfig config = runtimeConfig.getValue();
                String url = config.url().orElseThrow(() -> new ConfigurationException(
                        "quarkus.langchain4j.prompt.apicurio-registry.url must be set in order to load prompt templates from Apicurio Registry"));
                RegistryClient client = ApicurioPromptTemplateRegistryBuilder.createClient(url,
                        config.basicAuth().username().orElse(null),
                        config.basicAuth().password().orElse(null),
                        config.oauth2().tokenEndpoint().orElse(null),
                        config.oauth2().clientId().orElse(null),
                        config.oauth2().clientSecret().orElse(null),
                        config.oauth2().scope().orElse(null));
                return new ApicurioPromptTemplateRegistry(client, config.defaultGroup(), config.defaultVersion(),
                        config.cacheTtl(), Infrastructure.getDefaultWorkerPool());
            }
        };
    }

    /**
     * Eagerly resolves the prompt template of every {@link ApicurioSystemMessageProvider} used by an AI service, so that
     * the application fails to start if one of them cannot be fetched or parsed.
     */
    public void validateOnStartup(List<String> providerClassNames) {
        PromptApicurioRegistryRuntimeConfig config = runtimeConfig.getValue();
        if (!config.validateOnStartup() || providerClassNames.isEmpty()) {
            return;
        }
        ApicurioPromptTemplateRegistry registry = Arc.container().instance(ApicurioPromptTemplateRegistry.class).get();
        for (String providerClassName : providerClassNames) {
            PromptTemplateReference reference = instantiate(providerClassName).reference();
            ResolvedPromptTemplate resolved = registry.resolve(reference);
            log.infof("Validated prompt template '%s' (version %s) used by %s", registry.normalize(reference),
                    resolved.version(), providerClassName);
        }
    }

    private static ApicurioSystemMessageProvider instantiate(String className) {
        try {
            return (ApicurioSystemMessageProvider) Class
                    .forName(className, true, Thread.currentThread().getContextClassLoader())
                    .getConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to create " + className
                    + ": subclasses of ApicurioSystemMessageProvider must declare a public no-args constructor", e);
        }
    }
}
