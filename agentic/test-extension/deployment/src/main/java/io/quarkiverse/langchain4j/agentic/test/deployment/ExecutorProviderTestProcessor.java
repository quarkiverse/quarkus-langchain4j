package io.quarkiverse.langchain4j.agentic.test.deployment;

import org.jboss.jandex.DotName;

import io.quarkiverse.langchain4j.agentic.test.runtime.EnableProgrammaticExecutorProvider;
import io.quarkiverse.langchain4j.agentic.test.runtime.ProgrammaticExecutorProviderRecorder;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;

class ExecutorProviderTestProcessor {

    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    void registerProgrammaticProvider(CombinedIndexBuildItem index, ProgrammaticExecutorProviderRecorder recorder) {
        if (!index.getIndex().getAnnotations(DotName.createSimple(EnableProgrammaticExecutorProvider.class.getName()))
                .isEmpty()) {
            recorder.register();
        }
    }
}
