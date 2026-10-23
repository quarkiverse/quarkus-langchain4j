package io.quarkiverse.langchain4j.agentic.deployment;

import static org.assertj.core.api.Assertions.assertThat;

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
public class ProgrammaticExecutorProviderTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(ProgrammaticExecutorProviderTest.class,
                            CustomExecutorProviderTestSupport.class,
                            CustomExecutorProviderTestSupport.LeftAgent.class,
                            CustomExecutorProviderTestSupport.RightAgent.class,
                            CustomExecutorProviderTestSupport.ParallelWorkflow.class,
                            CustomExecutorProviderTestSupport.ThreadCapturingChatModel.class,
                            CustomExecutorProviderTestSupport.NamedExecutor.class,
                            CustomExecutorProviderTestSupport.ServiceProvider.class)
                    .addAsServiceProvider(ExecutorProvider.class, CustomExecutorProviderTestSupport.ServiceProvider.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "unused");

    @Inject
    CustomExecutorProviderTestSupport.ParallelWorkflow workflow;

    @Test
    void programmaticProviderTakesPrecedenceWithoutResolvingAnyExecutorDuringStartup() {
        assertThat(ExecutorProvider.get()).isInstanceOf(ProgrammaticExecutorProvider.class);
        assertThat(ProgrammaticExecutorProvider.executorCalls).isZero();
        assertThat(CustomExecutorProviderTestSupport.ServiceProvider.instances).isZero();

        CustomExecutorProviderTestSupport.workerThreads.clear();
        assertThat(workflow.run("test")).containsExactly("ok", "ok");

        assertThat(ProgrammaticExecutorProvider.executorCalls).isPositive();
        assertThat(CustomExecutorProviderTestSupport.ServiceProvider.instances).isZero();
        assertThat(CustomExecutorProviderTestSupport.workerThreads).hasSize(2)
                .allSatisfy(name -> assertThat(name).isEqualTo("programmatic-provider-worker"));
    }
}
