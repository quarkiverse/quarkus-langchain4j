package io.quarkiverse.langchain4j.deployment.devui;

import io.quarkiverse.langchain4j.runtime.devui.OpenWebUIJsonRPCService;
import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CuratedApplicationShutdownBuildItem;
import io.quarkus.devui.spi.JsonRPCProvidersBuildItem;

public final class OpenWebUIDevUIProcessor {

    @BuildStep(onlyIf = IsDevelopment.class)
    void registerOpenWebUiCard(BuildProducer<JsonRPCProvidersBuildItem> jsonRPCProviders,
            CuratedApplicationShutdownBuildItem closeBuildItem) {
        jsonRPCProviders.produce(new JsonRPCProvidersBuildItem(OpenWebUIJsonRPCService.class));
        closeBuildItem.addCloseTask(OpenWebUIJsonRPCService.CLOSE_TASK, true);
    }
}
