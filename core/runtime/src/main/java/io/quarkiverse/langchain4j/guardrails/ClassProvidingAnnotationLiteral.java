package io.quarkiverse.langchain4j.guardrails;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.util.AnnotationLiteral;

import dev.langchain4j.guardrail.Guardrail;

public abstract sealed class ClassProvidingAnnotationLiteral<A extends Annotation, G extends Guardrail>
        extends AnnotationLiteral<A> permits InputGuardrailsLiteral, OutputGuardrailsLiteral {
    private final List<String> classNames = new ArrayList<>();
    private transient volatile List<Class<G>> guardrailClasses;

    protected ClassProvidingAnnotationLiteral(List<String> classNames) {
        if (classNames != null) {
            this.classNames.addAll(classNames);
        }
    }

    /**
     * Needed because this class will be serialized & deserialized
     */
    public List<String> getClassNames() {
        return classNames;
    }

    /**
     * Needed because this class will be serialized & deserialized
     */
    public void setClassNames(List<String> classNames) {
        this.classNames.clear();

        if (classNames != null) {
            this.classNames.addAll(classNames);
        }
    }

    public Class<G>[] value() {
        return getClasses();
    }

    private boolean needsCacheInitialization() {
        return (this.guardrailClasses == null) || this.guardrailClasses.size() != this.classNames.size();
    }

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

    protected final Class<G>[] getClasses() {
        checkClassCache();
        return this.guardrailClasses.toArray(Class[]::new);
    }

    public boolean hasGuardrails() {
        checkClassCache();
        return !this.guardrailClasses.isEmpty();
    }
}
