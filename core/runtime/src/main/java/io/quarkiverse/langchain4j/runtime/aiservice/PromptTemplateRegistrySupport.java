package io.quarkiverse.langchain4j.runtime.aiservice;

import java.util.List;
import java.util.Map;
import java.util.Set;

import io.quarkiverse.langchain4j.prompt.PromptTemplateReference;
import io.quarkiverse.langchain4j.prompt.PromptTemplateRegistry;
import io.quarkiverse.langchain4j.prompt.PromptTemplateResolutionException;
import io.quarkiverse.langchain4j.prompt.PromptTemplateValidationException;
import io.quarkiverse.langchain4j.prompt.PromptVariable;
import io.quarkiverse.langchain4j.prompt.ResolvedPromptTemplate;
import io.quarkiverse.langchain4j.runtime.ResponseSchemaUtil;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;
import io.quarkus.arc.InstanceHandle;

/**
 * Bridges AI service methods annotated with {@code @SystemMessageFromRegistry} / {@code @UserMessageFromRegistry} and
 * the {@link PromptTemplateRegistry} bean provided by a registry extension.
 */
public final class PromptTemplateRegistrySupport {

    /**
     * Template variables that are always provided by Quarkus LangChain4j, regardless of the method signature.
     */
    public static final Set<String> IMPLICIT_VARIABLES = Set.of("chat_memory", ResponseSchemaUtil.templateParam());

    private PromptTemplateRegistrySupport() {
    }

    /**
     * Resolves the template referenced by {@code reference} and prepares {@code templateParams} for rendering it:
     * declared default values are applied for variables that are not bound, and an exception is thrown if a required
     * variable is missing.
     *
     * @return the template text to render
     */
    public static String resolveTemplateText(PromptTemplateReference reference, Map<String, Object> templateParams,
            AiServiceMethodCreateInfo createInfo) {
        ResolvedPromptTemplate resolved = registry().resolve(reference);
        for (PromptVariable variable : resolved.variables().values()) {
            if (variable.hasDefaultValue() && templateParams.get(variable.name()) == null) {
                templateParams.put(variable.name(), variable.defaultValue());
            }
        }
        List<String> missing = resolved.missingRequiredVariables(templateParams.keySet());
        if (!missing.isEmpty()) {
            throw new PromptTemplateValidationException(validationMessage(reference, resolved, missing,
                    createInfo.getInterfaceName() + "#" + createInfo.getMethodName()));
        }
        return resolved.text();
    }

    public static String validationMessage(PromptTemplateReference reference, ResolvedPromptTemplate resolved,
            List<String> missing, String location) {
        StringBuilder sb = new StringBuilder("Prompt template '").append(reference).append('\'');
        if (resolved.version() != null) {
            sb.append(" (version ").append(resolved.version()).append(')');
        }
        sb.append(" declares the required variable(s) ").append(missing)
                .append(" which are not bound by AI service method ").append(location)
                .append(". Add method parameters with matching names (or annotate parameters with @V(\"<name>\")).");
        return sb.toString();
    }

    private static PromptTemplateRegistry registry() {
        ArcContainer container = Arc.container();
        InstanceHandle<PromptTemplateRegistry> handle = container == null ? null
                : container.instance(PromptTemplateRegistry.class);
        if (handle == null || !handle.isAvailable()) {
            throw new PromptTemplateResolutionException(
                    "No " + PromptTemplateRegistry.class.getSimpleName()
                            + " bean is available, so prompt templates cannot be loaded from a registry. "
                            + "Add a registry extension such as 'quarkus-langchain4j-prompt-apicurio-registry' to the application "
                            + "and make sure it is configured and enabled.");
        }
        return handle.get();
    }
}
