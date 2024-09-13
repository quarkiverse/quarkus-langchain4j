package io.quarkiverse.langchain4j.deployment;

/**
 * Depending on the annotations hold, an ai service method parameter could be allowed as a template variable or not.
 * This enum holds the distinct kinds of allowance of such annotations.
 */
public enum MethodParameterAsTemplateVariableAllowance {
    /**
     * The annotation should be ignored
     */
    IGNORE,

    /**
     * The annotation force the parameter to be allowed as a template variable
     */
    FORCE_ALLOW,

    /**
     * Unless forced by another annotation, the annotation optionally denies the parameter as a template variable
     */
    OPTIONAL_DENY
}
