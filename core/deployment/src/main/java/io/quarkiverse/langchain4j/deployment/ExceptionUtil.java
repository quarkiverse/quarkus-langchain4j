package io.quarkiverse.langchain4j.deployment;

import static dev.langchain4j.service.IllegalConfigurationException.illegalConfiguration;

import org.jboss.jandex.MethodInfo;

import dev.langchain4j.service.IllegalConfigurationException;

class ExceptionUtil {

    static IllegalConfigurationException illegalConfigurationForMethod(String message, MethodInfo offendingMethod) {
        String effectiveMessage = message;
        if (message.endsWith(".")) {
            effectiveMessage = effectiveMessage.substring(0, effectiveMessage.length() - 1);
        }
        throw illegalConfiguration(effectiveMessage + ". Offending method is '"
                + offendingMethod.declaringClass().name().toString() + "#" + offendingMethod.name() + "'");
    }
}
