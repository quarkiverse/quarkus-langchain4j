package io.quarkiverse.langchain4j.evaluation.junit5.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

import io.quarkiverse.langchain4j.evaluation.junit5.EvaluationDisplayNameGenerator;

class EvaluationDisplayNameGeneratorTest {

    private final EvaluationDisplayNameGenerator generator = new EvaluationDisplayNameGenerator();

    @Test
    void shouldHumanizeClassName() {
        assertThat(generator.generateDisplayNameForClass(ChatbotEvaluationTest.class))
                .isEqualTo("Chatbot Evaluation");

        assertThat(generator.generateDisplayNameForClass(UserLoginIT.class))
                .isEqualTo("User Login");

        assertThat(generator.generateDisplayNameForClass(SimpleTests.class))
                .isEqualTo("Simple");
    }

    @Test
    void shouldHumanizeMethodNameWithTestPrefix() throws Exception {
        Method method = SampleTestClass.class.getDeclaredMethod("testUserLogin");

        assertThat(generator.generateDisplayNameForMethod(SampleTestClass.class, method))
                .isEqualTo("User Login");
    }

    @Test
    void shouldHumanizeMethodNameWithShouldPrefix() throws Exception {
        Method method = SampleTestClass.class.getDeclaredMethod("shouldReturnValidResponse");

        assertThat(generator.generateDisplayNameForMethod(SampleTestClass.class, method))
                .isEqualTo("Return Valid Response");
    }

    @Test
    void shouldHumanizeMethodNameWithVerifyPrefix() throws Exception {
        Method method = SampleTestClass.class.getDeclaredMethod("verifyErrorHandling");

        assertThat(generator.generateDisplayNameForMethod(SampleTestClass.class, method))
                .isEqualTo("Error Handling");
    }

    @Test
    void shouldHumanizeMethodNameWithCheckPrefix() throws Exception {
        Method method = SampleTestClass.class.getDeclaredMethod("checkDatabaseConnection");

        assertThat(generator.generateDisplayNameForMethod(SampleTestClass.class, method))
                .isEqualTo("Database Connection");
    }

    @Test
    void shouldHandleCamelCase() throws Exception {
        Method method = SampleTestClass.class.getDeclaredMethod("testHTTPSConnection");

        assertThat(generator.generateDisplayNameForMethod(SampleTestClass.class, method))
                .isEqualTo("HTTPS Connection");
    }

    @Test
    void shouldHandleMethodWithoutPrefix() throws Exception {
        Method method = SampleTestClass.class.getDeclaredMethod("evaluateChatbotResponse");

        assertThat(generator.generateDisplayNameForMethod(SampleTestClass.class, method))
                .isEqualTo("Evaluate Chatbot Response");
    }

    @Test
    void shouldHandleNestedClass() {
        assertThat(generator.generateDisplayNameForNestedClass(NestedEvaluationTest.class))
                .isEqualTo("Nested Evaluation");
    }

    // Test classes for reflection
    static class ChatbotEvaluationTest {
    }

    static class UserLoginIT {
    }

    static class SimpleTests {
    }

    static class NestedEvaluationTest {
    }

    static class SampleTestClass {
        void testUserLogin() {
        }

        void shouldReturnValidResponse() {
        }

        void verifyErrorHandling() {
        }

        void checkDatabaseConnection() {
        }

        void testHTTPSConnection() {
        }

        void evaluateChatbotResponse() {
        }
    }
}
