package io.quarkiverse.langchain4j.agentic.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.spi.ExecutorProvider;
import io.quarkus.arc.deployment.BeanContainerListenerBuildItem;
import io.quarkus.arc.runtime.BeanContainerListener;
import io.quarkus.deployment.builditem.StaticBytecodeRecorderBuildItem;
import io.quarkus.deployment.recording.BytecodeRecorderImpl;
import io.quarkus.test.QuarkusUnitTest;

public class ProgrammaticExecutorProviderTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(CustomExecutorProviderTestSupport.class,
                            CustomExecutorProviderTestSupport.LeftAgent.class,
                            CustomExecutorProviderTestSupport.RightAgent.class,
                            CustomExecutorProviderTestSupport.ParallelWorkflow.class,
                            CustomExecutorProviderTestSupport.ThreadCapturingChatModel.class,
                            CustomExecutorProviderTestSupport.NamedExecutor.class,
                            CustomExecutorProviderTestSupport.ServiceProvider.class,
                            CustomExecutorProviderTestSupport.ProgrammaticProvider.class,
                            CustomExecutorProviderTestSupport.ProviderRecorder.class)
                    .addAsServiceProvider(ExecutorProvider.class, CustomExecutorProviderTestSupport.ServiceProvider.class))
            .addBuildChainCustomizer(builder -> builder.addBuildStep(context -> {
                // Install the application provider before RUNTIME_INIT default registration.
                BytecodeRecorderImpl recorder = new BytecodeRecorderImpl(true,
                        ProgrammaticExecutorProviderTest.class.getName(), "installProvider", "installProvider", false);
                try {
                    Class<?> recorderClass = Thread.currentThread().getContextClassLoader()
                            .loadClass(CustomExecutorProviderTestSupport.ProviderRecorder.class.getName());
                    Object proxy = recorder.getRecordingProxy(recorderClass);
                    context.produce(new BeanContainerListenerBuildItem((BeanContainerListener) recorderClass
                            .getMethod("installProgrammaticProvider").invoke(proxy)));
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
                context.produce(new StaticBytecodeRecorderBuildItem(recorder));
            }).produces(BeanContainerListenerBuildItem.class).produces(StaticBytecodeRecorderBuildItem.class).build())
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "unused");

    @Inject
    CustomExecutorProviderTestSupport.ParallelWorkflow workflow;

    @Test
    void programmaticProviderTakesPrecedenceWithoutResolvingAnyExecutorDuringStartup() {
        assertThat(ExecutorProvider.get()).isInstanceOf(CustomExecutorProviderTestSupport.ProgrammaticProvider.class);
        assertThat(CustomExecutorProviderTestSupport.ProgrammaticProvider.executorCalls).isZero();
        assertThat(CustomExecutorProviderTestSupport.ServiceProvider.instances).isZero();

        CustomExecutorProviderTestSupport.workerThreads.clear();
        assertThat(workflow.run("test")).containsExactly("ok", "ok");

        assertThat(CustomExecutorProviderTestSupport.ProgrammaticProvider.executorCalls).isPositive();
        assertThat(CustomExecutorProviderTestSupport.ServiceProvider.instances).isZero();
        assertThat(CustomExecutorProviderTestSupport.workerThreads).hasSize(2)
                .allSatisfy(name -> assertThat(name).isEqualTo("programmatic-provider-worker"));
    }
}
