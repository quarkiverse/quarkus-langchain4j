package io.quarkiverse.langchain4j.deployment;

import java.util.function.Function;

import org.jboss.jandex.GenericSignature;
import org.jboss.jandex.Type;

class AsmUtil {

    static String getSignature(Type type, Function<String, Type> typeVariableSubstitution) {
        StringBuilder result = new StringBuilder();
        GenericSignature.forType(type, typeVariableSubstitution, result);
        return result.toString();
    }

}
