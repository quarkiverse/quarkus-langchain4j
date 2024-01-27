package io.quarkiverse.langchain4j.openai.deployment.devui;

import io.quarkiverse.langchain4j.openai.deployment.Langchain4jOpenAiBuildConfig;
import io.quarkiverse.langchain4j.openai.runtime.devui.OpenAiImagesJsonRPCService;
import io.quarkiverse.langchain4j.openai.runtime.devui.OpenAiModerationModelsJsonRPCService;
import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.devui.spi.JsonRPCProvidersBuildItem;
import io.quarkus.devui.spi.page.CardPageBuildItem;
import io.quarkus.devui.spi.page.Page;

public class OpenAiDevUIProcessor {

    @BuildStep(onlyIf = IsDevelopment.class)
    CardPageBuildItem cardPage(
            BuildProducer<JsonRPCProvidersBuildItem> producers,
            Langchain4jOpenAiBuildConfig config) {
        CardPageBuildItem card = new CardPageBuildItem();
        addImageModelPage(producers, config, card);
        addModerationModelPage(producers, config, card);
        return card;
    }

    private void addImageModelPage(
            BuildProducer<JsonRPCProvidersBuildItem> producers,
            Langchain4jOpenAiBuildConfig config,
            CardPageBuildItem card) {
        if (config.imageModel().enabled().orElse(true)) {
            card.addPage(Page.webComponentPageBuilder().title("Images")
                    .componentLink("qwc-images.js")
                    .icon("font-awesome-solid:palette"));
            producers.produce(new JsonRPCProvidersBuildItem(OpenAiImagesJsonRPCService.class));
        }
    }

    private void addModerationModelPage(BuildProducer<JsonRPCProvidersBuildItem> producers,
            Langchain4jOpenAiBuildConfig config,
            CardPageBuildItem card) {
        if (config.moderationModel().enabled().orElse(true)) {
            card.addPage(Page.webComponentPageBuilder().title("Moderation model")
                    .componentLink("qwc-moderation.js")
                    .icon("font-awesome-solid:triangle-exclamation"));
            producers.produce(new JsonRPCProvidersBuildItem(OpenAiModerationModelsJsonRPCService.class));
        }
    }

}
