package io.quarkiverse.langchain4j.test;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.microprofile.context.ManagedExecutor;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.internal.DefaultExecutorProvider;
import dev.langchain4j.spi.ExecutorProvider;
import io.quarkus.test.QuarkusUnitTest;

class DefaultExecutorProviderTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class));

    @Test
    void coreRegistersManagedExecutor() {
        assertThat(ExecutorProvider.get()).isNotNull();
        assertThat(ExecutorProvider.get().executor()).isInstanceOf(ManagedExecutor.class);
        assertThat(DefaultExecutorProvider.getDefaultExecutor())
                .isSameAs(ExecutorProvider.get().executor());
        assertThat(DefaultExecutorProvider.getDefaultExecutorService())
                .isSameAs(ExecutorProvider.get().executor());
    }
}
