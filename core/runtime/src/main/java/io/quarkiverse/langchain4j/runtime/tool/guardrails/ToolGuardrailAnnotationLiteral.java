package io.quarkiverse.langchain4j.runtime.tool.guardrails;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.util.AnnotationLiteral;

import io.quarkiverse.langchain4j.guardrails.ToolGuardrail;

/**
 * Base class for tool guardrail annotation literals.
 * <p>
 * This class handles lazy class loading of guardrail classes, enabling serialization
 * at build time and proper class resolution at runtime. It follows the same pattern
 * as the LLM guardrails' {@code ClassProvidingAnnotationLiteral}.
 * </p>
 * <p>
 * The class stores guardrail class names as strings and lazily loads the actual
 * {@code Class} objects when needed, using double-checked locking for thread safety.
 * </p>
 *
 * @param <A> the annotation type ( ToolInputGuardrails or ToolOutputGuardrails)
 * @param <G> the guardrail interface type (ToolInputGuardrail or ToolOutputGuardrail)
 */
public abstract sealed class ToolGuardrailAnnotationLiteral<A extends Annotation, G extends ToolGuardrail>
        extends AnnotationLiteral<A> permits ToolInputGuardrailsLiteral, ToolOutputGuardrailsLiteral {

    private final List<String> classNames = new ArrayList<>();
    private transient volatile List<Class<G>> guardrailClasses;

    /**
     * Constructs a new tool guardrail annotation literal with the given guardrail class names.
     *
     * @param classNames the list of guardrail class names (fully qualified)
     */
    protected ToolGuardrailAnnotationLiteral(List<String> classNames) {
        if (classNames != null) {
            this.classNames.addAll(classNames);
        }
    }

    /**
     * Gets the list of guardrail class names.
     * <p>
     * This method is needed for serialization/deserialization.
     * </p>
     *
     * @return the list of class names
     */
    public List<String> getClassNames() {
        return classNames;
    }

    /**
     * Gets the guardrail classes as an array, implementing the annotation's {@code value()} method.
     *
     * @return array of guardrail classes
     */
    public Class<? extends G>[] value() {
        return getClasses();
    }

    /**
     * Checks if the class cache needs to be initialized or refreshed.
     *
     * @return true if initialization is needed
     */
    private boolean needsCacheInitialization() {
        return (this.guardrailClasses == null) || this.guardrailClasses.size() != this.classNames.size();
    }

    /**
     * Initializes the class cache using lazy loading with double-checked locking.
     */
    private void checkClassCache() {
        // Using double-checked locking pattern for cache initialization
        if (needsCacheInitialization()) {
            synchronized (this) {
                if (needsCacheInitialization()) {
                    var classLoader = Thread.currentThread().getContextClassLoader();
                    this.guardrailClasses = this.classNames.stream()
                            .map(className -> {
                                try {
                                    return (Class<G>) Class.forName(className, false, classLoader);
                                } catch (ClassNotFoundException e) {
                                    throw new RuntimeException(e);
                                }
                            })
                            .toList();
                }
            }
        }
    }

    /**
     * Gets the guardrail classes, ensuring the cache is initialized.
     *
     * @return array of guardrail classes
     */
    protected final Class<? extends G>[] getClasses() {
        checkClassCache();
        return this.guardrailClasses.toArray(Class[]::new);
    }

    /**
     * Checks if any guardrails are configured.
     * <p>
     * This method does not trigger class loading and is safe to call during
     * metadata inspection.
     * </p>
     *
     * @return true if at least one guardrail is configured, false otherwise
     */
    public boolean hasGuardrails() {
        return !this.classNames.isEmpty();
    }
}
