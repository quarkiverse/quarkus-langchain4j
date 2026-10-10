package io.quarkiverse.langchain4j.agentic.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.concurrent.Executor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.langchain4j.spi.ExecutorProvider;

public class ExecutorProviderRegistrationTest {

    private ExecutorProvider previousProvider;
    private ClassLoader previousClassLoader;

    @BeforeEach
    void saveState() {
        previousProvider = ExecutorProvider.get();
        previousClassLoader = Thread.currentThread().getContextClassLoader();
        ExecutorProvider.set(null);
        TestExecutorProvider.instances = 0;
    }

    @AfterEach
    void restoreState() {
        Thread.currentThread().setContextClassLoader(previousClassLoader);
        ExecutorProvider.set(previousProvider);
    }

    @Test
    void preservesProgrammaticProviderWithoutResolvingItsExecutor() {
        ExecutorProvider provider = () -> {
            throw new AssertionError("Registration must not resolve the user's executor");
        };
        ExecutorProvider.set(provider);

        new AgenticRecorder().registerDefaultExecutorProvider();

        assertSame(provider, ExecutorProvider.get());
    }

    @Test
    void preservesServiceLoaderProviderWithoutInstantiatingIt() {
        useServiceProviderClassLoader();

        new AgenticRecorder().registerDefaultExecutorProvider();

        assertNull(ExecutorProvider.get(), "The ServiceLoader provider must not be shadowed");
        assertEquals(0, TestExecutorProvider.instances);
    }

    @Test
    void preservesProgrammaticProviderWhenServiceLoaderProviderIsAlsoPresent() {
        useServiceProviderClassLoader();
        ExecutorProvider provider = () -> Runnable::run;
        ExecutorProvider.set(provider);

        new AgenticRecorder().registerDefaultExecutorProvider();

        assertSame(provider, ExecutorProvider.get());
        assertEquals(0, TestExecutorProvider.instances);
    }

    private void useServiceProviderClassLoader() {
        Thread.currentThread().setContextClassLoader(new ClassLoader(previousClassLoader) {
            @Override
            public Enumeration<URL> getResources(String name) throws IOException {
                if (name.equals("META-INF/services/" + ExecutorProvider.class.getName())) {
                    return Collections.enumeration(Collections.singletonList(
                            ExecutorProviderRegistrationTest.class.getResource("/executor-provider-test.txt")));
                }
                return super.getResources(name);
            }
        });
    }

    public static class TestExecutorProvider implements ExecutorProvider {
        static int instances;

        public TestExecutorProvider() {
            instances++;
        }

        @Override
        public Executor executor() {
            return Runnable::run;
        }
    }
}
