package io.quarkiverse.langchain4j.openai.deployment;

import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.IndexDependencyBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ExcludeConfigBuildItem;

public class OpenAiCommonProcessor {

    @BuildStep
    void indexDependencies(BuildProducer<IndexDependencyBuildItem> producer) {
        producer.produce(new IndexDependencyBuildItem("dev.ai4j", "openai4j"));
    }

    @BuildStep
    void excludeNativeDirectives(BuildProducer<ExcludeConfigBuildItem> nativeImageExclusions) {
        String jarFileRegex = "dev\\.ai4j\\.openai4j";
        nativeImageExclusions.produce(
                new ExcludeConfigBuildItem(jarFileRegex, "/META-INF/native-image/reflect-config\\.json"));
        nativeImageExclusions.produce(
                new ExcludeConfigBuildItem(jarFileRegex,
                        "/META-INF/native-image/dev\\.ai4j/openai4j/proxy-config\\.json"));
    }
}
