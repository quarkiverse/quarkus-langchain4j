package io.quarkiverse.langchain4j.prompt.runtime.apicurio;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.jboss.logging.Logger;

import io.apicurio.registry.rest.client.RegistryClient;
import io.quarkiverse.langchain4j.prompt.PromptTemplateReference;
import io.quarkiverse.langchain4j.prompt.PromptTemplateUsage;
import io.quarkiverse.langchain4j.prompt.PromptTemplateValidationException;
import io.quarkiverse.langchain4j.prompt.ResolvedPromptTemplate;
import io.quarkiverse.langchain4j.prompt.runtime.apicurio.config.PromptApicurioRegistryRuntimeConfig;
import io.quarkiverse.langchain4j.runtime.aiservice.PromptTemplateRegistrySupport;
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
     * Eagerly resolves every prompt template used by an AI service and verifies that the variables it declares as
     * required are bound by the method using it. Fails the application startup on the first resolution error, or if
     * any template has unbound required variables.
     */
    public void validateOnStartup(List<PromptTemplateUsage> usages) {
        PromptApicurioRegistryRuntimeConfig config = runtimeConfig.getValue();
        if (!config.validateOnStartup() || usages.isEmpty()) {
            return;
        }
        ApicurioPromptTemplateRegistry registry = Arc.container().instance(ApicurioPromptTemplateRegistry.class).get();
        List<String> problems = new ArrayList<>();
        for (PromptTemplateUsage usage : usages) {
            PromptTemplateReference reference = usage.reference();
            ResolvedPromptTemplate resolved = registry.resolve(reference);
            log.infof("Validated prompt template '%s' (version %s) used by %s", registry.normalize(reference),
                    resolved.version(), usage.location());
            List<String> missing = resolved.missingRequiredVariables(usage.boundVariables());
            if (!missing.isEmpty()) {
                problems.add(PromptTemplateRegistrySupport.validationMessage(registry.normalize(reference), resolved, missing,
                        usage.location()));
            }
        }
        if (!problems.isEmpty()) {
            throw new PromptTemplateValidationException(String.join(System.lineSeparator(), problems));
        }
    }
}
