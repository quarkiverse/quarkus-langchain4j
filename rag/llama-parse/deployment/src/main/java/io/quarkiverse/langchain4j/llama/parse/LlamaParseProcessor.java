package io.quarkiverse.langchain4j.llama.parse;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import org.jboss.jandex.DotName;

public class LlamaParseProcessor {

    private static final String FEATURE = "langchain4j-llamaparse";
    private static final DotName LLAMA_PARSE = DotName.createSimple(LlamaParseProcessor.class);

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    public void createBean() {
    }
}
