package org.acme.examples.aiservices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.spi.DeploymentException;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.Tool;
import io.quarkus.test.QuarkusUnitTest;

@DisplayName("@Tool validation")
public class ToolValidationTest {

    @Nested
    @DisplayName("Duplicated tools detection")
    class DuplicatedTools {
        @RegisterExtension
        static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
                .setArchiveProducer(
                        () -> ShrinkWrap.create(JavaArchive.class).addClasses(MyFirstTool.class, MySecondTool.class))
                .assertException(t -> {
                    assertThat(t)
                            .isInstanceOf(DeploymentException.class)
                            .hasCauseInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("A tool with the name 'myTool'");
                });

        @Test
        void test() {
            fail("Should not be called");
        }
    }

    @Nested
    @DisplayName("No parameter detection")
    class NoParameterTools {
        @RegisterExtension
        static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
                .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClasses(MyInvalidTool.class))
                .assertException(t -> {
                    assertThat(t)
                            .isInstanceOf(DeploymentException.class)
                            .hasCauseInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("Tool method 'myTool' on class");
                });

        @Test
        void test() {
            fail("Should not be called");
        }
    }

    @Nested
    @DisplayName("Abstract tool detection")
    class AbstractTools {
        @RegisterExtension
        static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
                .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClasses(MyAbstractTool.class))
                .assertException(t -> {
                    assertThat(t)
                            .isInstanceOf(DeploymentException.class)
                            .hasCauseInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("@Tool is only supported on non-abstract classes");
                });

        @Test
        void test() {
            fail("Should not be called");
        }
    }

    @Nested
    @DisplayName("Interface tool detection")
    class InterfaceTools {
        @RegisterExtension
        static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
                .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClasses(MyAbstractTool.class))
                .assertException(t -> {
                    assertThat(t)
                            .isInstanceOf(DeploymentException.class)
                            .hasCauseInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("@Tool is only supported on non-abstract classes");
                });

        @Test
        void test() {
            fail("Should not be called");
        }
    }

    @ApplicationScoped
    public static class MyFirstTool {

        @Tool
        public String myTool(String input) {
            return input;
        }

    }

    @ApplicationScoped
    public static class MySecondTool {

        @Tool
        public String myTool(String input) {
            return input;
        }

    }

    @ApplicationScoped
    public static class MyInvalidTool {

        @Tool
        public String myTool() {
            return "foo";
        }

    }

    public static abstract class MyAbstractTool {

        @Tool
        public String myTool(String s) {
            return "foo";
        }

    }

    public static interface MyInterfaceTool {

        @Tool
        default String myTool(String s) {
            return "foo";
        }

    }

}
