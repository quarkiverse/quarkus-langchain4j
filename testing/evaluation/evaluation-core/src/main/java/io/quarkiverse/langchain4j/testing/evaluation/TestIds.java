package io.quarkiverse.langchain4j.testing.evaluation;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Finds the JUnit test method that is running, to derive the id of its baseline.
 * <p>
 * The stack is walked instead of keeping the running test in a holder, because under {@code @QuarkusTest} the test
 * runs in another classloader than the extension, and a static holder would not be shared. Annotations are
 * recognized by type name, for the same reason.
 * </p>
 */
final class TestIds {

    private static final String TESTABLE = "org.junit.platform.commons.annotation.Testable";

    private TestIds() {
    }

    /**
     * @return {@code <class name>/<method name>} of the closest test method on the stack, or empty if there is none
     */
    static Optional<String> current() {
        return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(frames -> frames
                .filter(frame -> !frame.getMethodName().startsWith("lambda$"))
                .filter(frame -> isTestMethod(frame.getDeclaringClass(), frame.getMethodName()))
                .map(frame -> frame.getClassName() + "/" + frame.getMethodName())
                .findFirst());
    }

    private static boolean isTestMethod(Class<?> type, String name) {
        try {
            return Stream.of(type.getDeclaredMethods()).filter(m -> m.getName().equals(name))
                    .anyMatch(TestIds::isTestable);
        } catch (LinkageError e) {
            return false;
        }
    }

    private static boolean isTestable(Method method) {
        return Stream.of(method.getAnnotations()).anyMatch(a -> isTestable(a.annotationType(), new HashSet<>()));
    }

    // @ParameterizedTest is meta-annotated with @TestTemplate, which is meta-annotated with @Testable
    private static boolean isTestable(Class<? extends Annotation> annotation, Set<Class<?>> visited) {
        if (TESTABLE.equals(annotation.getName())) {
            return true;
        }
        return visited.add(annotation)
                && Stream.of(annotation.getAnnotations()).anyMatch(a -> isTestable(a.annotationType(), visited));
    }
}
