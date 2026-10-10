package io.quarkiverse.langchain4j.prompt.runtime.apicurio;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.input.PromptTemplate;
import io.quarkiverse.langchain4j.runtime.aiservice.SystemMessageProviderWithContext;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;
import io.quarkus.arc.InstanceHandle;

/**
 * A {@link SystemMessageProviderWithContext} that loads the system message of an AI service from an Apicurio Registry
 * {@code PROMPT_TEMPLATE} artifact.
 * <p>
 * Extend it to bind a template to an AI service, and reference the subclass from
 * {@code @RegisterAiService(systemMessageProviderSupplier = ...)}:
 *
 * <pre>
 * public class CustomerSupportPrompt extends ApicurioSystemMessageProvider {
 *
 *     public CustomerSupportPrompt() {
 *         super("customer-support");
 *     }
 * }
 * </pre>
 *
 * The template is rendered with the variables returned by {@link #variables(InvocationContext)}, complemented by the
 * default values declared in the artifact. Subclasses must declare a public no-args constructor.
 * <p>
 * As for any system message provider, an explicit {@code @SystemMessage} on an AI service method takes precedence.
 */
public abstract class ApicurioSystemMessageProvider implements SystemMessageProviderWithContext {

    private final PromptTemplateReference reference;

    /**
     * @param artifactId the artifact holding the template, resolved with the configured default group and version
     */
    protected ApicurioSystemMessageProvider(String artifactId) {
        this(PromptTemplateReference.of(artifactId));
    }

    /**
     * @param groupId the group of the artifact, or {@code null} to use the configured default group
     * @param artifactId the artifact holding the template
     * @param version the version expression (e.g. {@code 1.0.0}, {@code branch=latest} or {@code latest}), or
     *        {@code null} to use the configured default version
     */
    protected ApicurioSystemMessageProvider(String groupId, String artifactId, String version) {
        this(PromptTemplateReference.of(groupId, artifactId, version));
    }

    protected ApicurioSystemMessageProvider(PromptTemplateReference reference) {
        this.reference = Objects.requireNonNull(reference, "reference is required");
    }

    /**
     * @return the template used by this provider
     */
    public PromptTemplateReference reference() {
        return reference;
    }

    @Override
    public Optional<String> getSystemMessage(InvocationContext context) {
        ApicurioPromptTemplateRegistry registry = registry();
        ResolvedPromptTemplate resolved = registry.resolve(reference);

        Map<String, Object> templateParams = new HashMap<>(variables(context));
        for (PromptVariable variable : resolved.variables().values()) {
            if (variable.hasDefaultValue() && templateParams.get(variable.name()) == null) {
                templateParams.put(variable.name(), variable.defaultValue());
            }
        }
        List<String> missing = resolved.missingRequiredVariables(templateParams.keySet());
        if (!missing.isEmpty()) {
            throw new PromptTemplateValidationException(validationMessage(registry.normalize(reference), resolved, missing));
        }
        return Optional.of(PromptTemplate.from(resolved.text()).apply(templateParams).text());
    }

    /**
     * Returns the values of the template variables for the given invocation.
     * <p>
     * Override this method to bind the variables declared by the template, for example from the method arguments
     * ({@link InvocationContext#methodArguments()}), the memory id ({@link InvocationContext#chatMemoryId()}) or CDI
     * beans. Variables that are not returned fall back to the default values declared in the artifact.
     *
     * @return the template variables, never {@code null}
     */
    protected Map<String, Object> variables(InvocationContext context) {
        return Map.of();
    }

    /**
     * @return the registry used to resolve the template, by default the bean configured with
     *         {@code quarkus.langchain4j.prompt.apicurio-registry.*}
     */
    protected ApicurioPromptTemplateRegistry registry() {
        ArcContainer container = Arc.container();
        InstanceHandle<ApicurioPromptTemplateRegistry> handle = container == null ? null
                : container.instance(ApicurioPromptTemplateRegistry.class);
        if (handle == null || !handle.isAvailable()) {
            throw new PromptTemplateResolutionException("No " + ApicurioPromptTemplateRegistry.class.getSimpleName()
                    + " bean is available, so prompt template '" + reference + "' cannot be loaded. "
                    + "Make sure quarkus.langchain4j.prompt.apicurio-registry.enabled is not set to false, "
                    + "or override registry() to provide one.");
        }
        return handle.get();
    }

    private String validationMessage(PromptTemplateReference normalized, ResolvedPromptTemplate resolved,
            List<String> missing) {
        StringBuilder sb = new StringBuilder("Prompt template '").append(normalized).append('\'');
        if (resolved.version() != null) {
            sb.append(" (version ").append(resolved.version()).append(')');
        }
        sb.append(" declares the required variable(s) ").append(missing)
                .append(" which are not provided by ").append(getClass().getName())
                .append(". Override variables(InvocationContext) to provide them.");
        return sb.toString();
    }
}
