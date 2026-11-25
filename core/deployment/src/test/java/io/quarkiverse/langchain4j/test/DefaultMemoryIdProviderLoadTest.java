package io.quarkiverse.langchain4j.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Objects;
import java.util.logging.LogRecord;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.langchain4j.runtime.aiservice.AiServiceMethodImplementationSupport;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Regression test for loading {@link io.quarkiverse.langchain4j.spi.DefaultMemoryIdProvider} implementations with
 * {@link java.util.ServiceLoader} instead of langchain4j's {@code ServiceHelper}: both the core provider and the
 * WebSockets Next provider are on the classpath here, and {@code ServiceHelper} would log a classpath-order-dependent
 * "Found 2 implementations ... using X and ignoring Y" warning (and drop all but one provider), which does not apply
 * since all providers are kept and sorted by priority.
 */
public class DefaultMemoryIdProviderLoadTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .setLogRecordPredicate(record -> true)
            .assertLogRecords(DefaultMemoryIdProviderLoadTest::verifyNoAmbiguityWarning);

    private static void verifyNoAmbiguityWarning(List<LogRecord> logRecords) {
        List<String> ambiguityWarnings = logRecords.stream()
                .map(LogRecord::getMessage)
                .filter(Objects::nonNull)
                .filter(message -> message
                        .contains("implementations of io.quarkiverse.langchain4j.spi.DefaultMemoryIdProvider"))
                .toList();
        assertThat(ambiguityWarnings)
                .as("default memory ID providers are all kept and no ServiceHelper classpath-order warning is logged")
                .isEmpty();
    }

    @Test
    void loadingProvidersDoesNotLogAmbiguityWarning() throws ClassNotFoundException {
        // triggers the static initializer that loads the DefaultMemoryIdProvider implementations
        Class.forName(AiServiceMethodImplementationSupport.class.getName(), true,
                Thread.currentThread().getContextClassLoader());
    }
}
