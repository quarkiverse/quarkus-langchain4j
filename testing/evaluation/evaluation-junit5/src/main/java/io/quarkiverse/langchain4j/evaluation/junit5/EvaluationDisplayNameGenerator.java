package io.quarkiverse.langchain4j.evaluation.junit5;

import java.lang.reflect.Method;

import org.junit.jupiter.api.DisplayNameGenerator;

/**
 * Custom display name generator for evaluation tests.
 * <p>
 * This generator provides readable display names for evaluation tests by:
 * <ul>
 * <li>Converting camelCase to readable format (e.g., "testUserLogin" becomes "test User Login")</li>
 * <li>Removing common test prefixes ("test", "should", "verify")</li>
 * <li>Works seamlessly with {@link EvaluationScoreReporter} to show scores after execution</li>
 * </ul>
 * </p>
 */
public class EvaluationDisplayNameGenerator implements DisplayNameGenerator {

    @Override
    public String generateDisplayNameForClass(Class<?> testClass) {
        return humanizeClassName(testClass.getSimpleName());
    }

    @Override
    public String generateDisplayNameForNestedClass(Class<?> nestedClass) {
        return humanizeClassName(nestedClass.getSimpleName());
    }

    @Override
    public String generateDisplayNameForMethod(Class<?> testClass, Method testMethod) {
        return humanizeMethodName(testMethod.getName());
    }

    private String humanizeClassName(String className) {
        // Remove common test suffixes
        String name = className
                .replaceAll("Test$", "")
                .replaceAll("IT$", "")
                .replaceAll("Tests$", "");

        return splitCamelCase(name);
    }

    private String humanizeMethodName(String methodName) {
        // Remove common test prefixes
        String name = methodName
                .replaceAll("^test", "")
                .replaceAll("^should", "")
                .replaceAll("^verify", "")
                .replaceAll("^check", "");

        return splitCamelCase(name);
    }

    private String splitCamelCase(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        // Insert space before uppercase letters (except at the start)
        // Handle sequences of capitals (e.g., "HTTPSConnection" → "HTTPS Connection")
        String result = input.replaceAll(
                "([a-z])([A-Z])", "$1 $2") // lowercase followed by uppercase
                .replaceAll(
                        "([A-Z])([A-Z][a-z])", "$1 $2"); // uppercase followed by uppercase+lowercase

        // Trim leading/trailing spaces and capitalize first letter
        result = result.trim();
        if (!result.isEmpty()) {
            result = Character.toUpperCase(result.charAt(0)) + result.substring(1);
        }

        return result;
    }
}
