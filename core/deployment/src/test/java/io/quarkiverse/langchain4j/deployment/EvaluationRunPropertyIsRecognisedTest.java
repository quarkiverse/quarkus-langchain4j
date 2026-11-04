package io.quarkiverse.langchain4j.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;

/**
 * The evaluation framework marks each test run with {@code quarkus.langchain4j.evaluation.run}. Quarkus warns about
 * every {@code quarkus.*} property no extension declares, so this one must be declared.
 */
class EvaluationRunPropertyIsRecognisedTest {

    private static final String KEY = "quarkus.langchain4j.evaluation.run";

    // The handler is registered before the extension below starts Quarkus, which logs the warning. The test method
    // runs in another class loader, so the handler reports through a system property, not a static field.
    private static final String WARNED = EvaluationRunPropertyIsRecognisedTest.class.getName() + ".warned";
    private static final Handler HANDLER = register();

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey(KEY, "a-run");

    private static Handler register() {
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                String message = String.valueOf(record.getMessage());
                if (record.getParameters() != null) {
                    for (Object parameter : record.getParameters()) {
                        message += " " + parameter;
                    }
                }
                if (message.contains("Unrecognized configuration") && message.contains(KEY)) {
                    System.setProperty(WARNED, message);
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        Logger.getLogger("io.quarkus.config").addHandler(handler);
        return handler;
    }

    @AfterAll
    static void unregister() {
        Logger.getLogger("io.quarkus.config").removeHandler(HANDLER);
        System.clearProperty(WARNED);
    }

    @Test
    void noUnrecognisedConfigurationWarning() {
        assertThat(System.getProperty(WARNED)).isNull();
    }
}
