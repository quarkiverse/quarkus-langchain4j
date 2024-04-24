package io.quarkiverse.langchain4j.deployment;

import org.jboss.jandex.MethodInfo;

public final class MethodUtil {

    private MethodUtil() {
        //Utility class
    }

    /**
     * Returns the signature of a method
     *
     * @param method the method to get the signature from
     * @return the signature of the method
     */
    public static String signature(MethodInfo method) {
        StringBuilder signature = new StringBuilder();
        signature.append(method.name());
        signature.append("(");
        for (int i = 0; i < method.parameters().size(); i++) {
            signature.append(method.parameters().get(i).type());
            if (i < method.parameters().size() - 1) {
                signature.append(", ");
            }
        }
        signature.append(")");
        return signature.toString();
    }

    /**
     * Checks if the signatures of two methods matches
     *
     * @param a the first method to check
     * @param b the second method to compare
     * @return true if both method signatures match, false otherwise
     */
    public static boolean methodSignaturesMatch(MethodInfo a, MethodInfo b) {
        if (!a.name().equals(b.name())) {
            return false;
        }
        if (a.parameters().size() != b.parameters().size()) {
            return false;
        }

        for (int i = 0; i < a.parameters().size(); i++) {
            if (!a.parameters().get(i).type().equals(b.parameters().get(i).type())) {
                return false;
            }
        }
        return true;
    }
}
