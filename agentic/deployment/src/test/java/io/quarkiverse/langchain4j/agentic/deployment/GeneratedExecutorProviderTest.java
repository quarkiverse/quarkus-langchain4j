package io.quarkiverse.langchain4j.agentic.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.spi.ExecutorProvider;
import io.quarkus.deployment.builditem.GeneratedServiceProviderBuildItem;
import io.quarkus.test.QuarkusUnitTest;

public class GeneratedExecutorProviderTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(CustomExecutorProviderTestSupport.class,
                            CustomExecutorProviderTestSupport.LeftAgent.class,
                            CustomExecutorProviderTestSupport.RightAgent.class,
                            CustomExecutorProviderTestSupport.ParallelWorkflow.class,
                            CustomExecutorProviderTestSupport.ThreadCapturingChatModel.class,
                            CustomExecutorProviderTestSupport.NamedExecutor.class,
                            CustomExecutorProviderTestSupport.ServiceProvider.class))
            .addBuildChainCustomizer(chain -> chain.addBuildStep(context -> context.produce(
                    new GeneratedServiceProviderBuildItem(ExecutorProvider.class.getName(),
                            CustomExecutorProviderTestSupport.ServiceProvider.class.getName())))
                    .produces(GeneratedServiceProviderBuildItem.class)
                    .build())
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "unused");

    @Inject
    CustomExecutorProviderTestSupport.ParallelWorkflow workflow;

    @Test
    void generatedServiceProviderIsNotShadowedOrInstantiatedDuringStartup() {
        assertThat(ExecutorProvider.get()).isNull();
        assertThat(CustomExecutorProviderTestSupport.ServiceProvider.instances).isZero();
        assertThat(CustomExecutorProviderTestSupport.ServiceProvider.executorCalls).isZero();

        CustomExecutorProviderTestSupport.workerThreads.clear();
        assertThat(workflow.run("test")).containsExactly("ok", "ok");

        assertThat(CustomExecutorProviderTestSupport.ServiceProvider.instances).isPositive();
        assertThat(CustomExecutorProviderTestSupport.ServiceProvider.executorCalls).isPositive();
        assertThat(CustomExecutorProviderTestSupport.workerThreads).hasSize(2)
                .allSatisfy(name -> assertThat(name).isEqualTo("service-provider-worker"));
    }
}
