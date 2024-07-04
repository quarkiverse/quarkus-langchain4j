package io.quarkiverse.langchain4j.openai.deployment.devui;

import java.util.Map;

import io.quarkiverse.langchain4j.deployment.devui.AdditionalDevUiCardBuildItem;
import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;

public final class OpenAiCommonDevUIProcessor {

    @BuildStep(onlyIf = IsDevelopment.class)
    void registerOpenWebUiCard(BuildProducer<AdditionalDevUiCardBuildItem> producer) {
        producer.produce(new AdditionalDevUiCardBuildItem("Open WebUI", "font-awesome-solid:globe", "qwc-open-webui.js",
                Map.of("envVarMappings",
                        Map.of("OPENAI_API_KEY", "quarkus.langchain4j.openai.api-key"))));
    }
}
