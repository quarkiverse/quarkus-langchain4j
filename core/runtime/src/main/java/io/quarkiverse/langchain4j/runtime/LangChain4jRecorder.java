package io.quarkiverse.langchain4j.runtime;

import java.util.ServiceLoader;
import java.util.concurrent.Executor;

import org.eclipse.microprofile.context.ManagedExecutor;
import org.jboss.logging.Logger;

import dev.langchain4j.spi.ExecutorProvider;
import io.quarkiverse.langchain4j.QuarkusPromptTemplateFactory;
import io.quarkus.arc.Arc;
import io.quarkus.runtime.ShutdownContext;
import io.quarkus.runtime.annotations.Recorder;
import io.quarkus.runtime.annotations.RuntimeInit;

@Recorder
public class LangChain4jRecorder {

    private static final Logger log = Logger.getLogger(LangChain4jRecorder.class);

    @RuntimeInit
    public void registerDefaultExecutorProvider(ShutdownContext shutdown) {
        ExecutorProvider previous = ExecutorProvider.get();
        // LangChain4j prefers a programmatic provider over a ServiceLoader one,
        // so setting ours would silently shadow a user-supplied provider.
        if (previous != null || ServiceLoader.load(ExecutorProvider.class).stream().findAny().isPresent()) {
            return;
        }
        ManagedExecutor managedExecutor = Arc.container().instance(ManagedExecutor.class).get();
        if (managedExecutor == null) {
            log.warn("ManagedExecutor not available — LangChain4j will use its default executor "
                    + "without CDI/OTel/Security context propagation");
            return;
        }
        ExecutorProvider provider = new ExecutorProvider() {
            @Override
            public Executor executor() {
                return managedExecutor;
            }
        };
        ExecutorProvider.set(provider);
        shutdown.addShutdownTask(new Runnable() {
            @Override
            public void run() {
                if (ExecutorProvider.get() == provider) {
                    ExecutorProvider.set(previous);
                }
            }
        });
    }

    public void cleanUp(ShutdownContext shutdown) {
        shutdown.addShutdownTask(new Runnable() {
            @Override
            public void run() {
                StructuredPromptsRecorder.clearTemplates();
                QuarkusPromptTemplateFactory.clear();
                AiServicesRecorder.clearMetadata();
                ToolsRecorder.clearMetadata();
            }
        });
    }
}
