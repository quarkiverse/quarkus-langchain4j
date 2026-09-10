package io.quarkiverse.langchain4j;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Loads the user message template of an AI service method from a prompt template registry (such as Apicurio Registry)
 * at runtime instead of declaring it inline with {@link dev.langchain4j.service.UserMessage}.
 * <p>
 * The referenced artifact must be a prompt template (for Apicurio Registry, an artifact of type
 * {@code PROMPT_TEMPLATE}). Its {@code template} is rendered with the same Qute-based engine used for inline
 * templates, so method parameters (or parameters annotated with {@link dev.langchain4j.service.V}) are bound to the
 * template variables by name. Variables declared by the registry as required but not bound by the method are reported
 * as errors, and declared default values are applied when a variable is not bound.
 * <p>
 * Resolution is performed by the {@link io.quarkiverse.langchain4j.prompt.PromptTemplateRegistry} bean provided by a
 * registry extension, for example {@code quarkus-langchain4j-prompt-apicurio-registry}.
 * <p>
 * {@code @UserMessageFromRegistry} cannot be combined with {@link dev.langchain4j.service.UserMessage} on the same
 * method.
 */
@Retention(RUNTIME)
@Target({ METHOD })
public @interface UserMessageFromRegistry {

    /**
     * The identifier of the prompt template artifact in the registry.
     */
    String value();

    /**
     * The group of the artifact. When empty, the default group configured for the registry extension is used.
     */
    String groupId() default "";

    /**
     * The version (or version expression) of the artifact to use, for example {@code 1.0.0} or {@code branch=latest}.
     * When empty, the default version configured for the registry extension is used (usually the latest version).
     */
    String version() default "";
}
