package io.quarkiverse.langchain4j.agentic.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ServiceLoader;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.spi.ExecutorProvider;
import io.quarkiverse.langchain4j.agentic.test.runtime.EnableProgrammaticExecutorProvider;
import io.quarkiverse.langchain4j.agentic.test.runtime.ProgrammaticExecutorProvider;
import io.quarkus.test.QuarkusUnitTest;

@EnableProgrammaticExecutorProvider
public class ProgrammaticOnlyExecutorProviderTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(ProgrammaticOnlyExecutorProviderTest.class,
                            CustomExecutorProviderTestSupport.class,
                            CustomExecutorProviderTestSupport.LeftAgent.class,
                            CustomExecutorProviderTestSupport.RightAgent.class,
                            CustomExecutorProviderTestSupport.ParallelWorkflow.class,
                            CustomExecutorProviderTestSupport.ThreadCapturingChatModel.class,
                            CustomExecutorProviderTestSupport.NamedExecutor.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "unused");

    @Inject
    CustomExecutorProviderTestSupport.ParallelWorkflow workflow;

    @Test
    void programmaticProviderIsPreservedWithoutAnyServiceProvider() {
        assertThat(ServiceLoader.load(ExecutorProvider.class).iterator().hasNext()).isFalse();
        assertThat(ExecutorProvider.get()).isInstanceOf(ProgrammaticExecutorProvider.class);
        assertThat(ProgrammaticExecutorProvider.executorCalls).isZero();

        CustomExecutorProviderTestSupport.workerThreads.clear();
        assertThat(workflow.run("test")).containsExactly("ok", "ok");

        assertThat(ProgrammaticExecutorProvider.executorCalls).isPositive();
        assertThat(CustomExecutorProviderTestSupport.workerThreads).hasSize(2)
                .allSatisfy(name -> assertThat(name).isEqualTo("programmatic-provider-worker"));
    }
}
