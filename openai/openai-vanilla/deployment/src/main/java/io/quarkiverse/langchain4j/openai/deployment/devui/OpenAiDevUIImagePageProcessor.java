package io.quarkiverse.langchain4j.openai.deployment.devui;

import io.quarkiverse.langchain4j.openai.deployment.Langchain4jOpenAiBuildConfig;
import io.quarkiverse.langchain4j.openai.runtime.devui.OpenAiImagesJsonRPCService;
import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.devui.spi.JsonRPCProvidersBuildItem;
import io.quarkus.devui.spi.page.CardPageBuildItem;
import io.quarkus.devui.spi.page.Page;

public class OpenAiDevUIImagePageProcessor {

    @BuildStep(onlyIf = IsDevelopment.class)
    CardPageBuildItem cardPage(
            BuildProducer<JsonRPCProvidersBuildItem> producers,
            Langchain4jOpenAiBuildConfig config) {
        if (config.imageModel().enabled().orElse(true)) {
            CardPageBuildItem card = new CardPageBuildItem();
            card.addPage(Page.webComponentPageBuilder().title("Images")
                    .componentLink("qwc-images.js")
                    .icon("font-awesome-solid:palette"));
            producers.produce(new JsonRPCProvidersBuildItem(OpenAiImagesJsonRPCService.class));
            return card;
        } else {
            return null;
        }
    }

}
