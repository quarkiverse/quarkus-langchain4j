package io.quarkiverse.langchain4j.openai.deployment.devui;

import java.util.ArrayList;
import java.util.List;

import io.quarkiverse.langchain4j.deployment.items.SelectedImageModelProviderBuildItem;
import io.quarkiverse.langchain4j.deployment.items.SelectedModerationModelProviderBuildItem;
import io.quarkiverse.langchain4j.openai.deployment.LangChain4jOpenAiBuildConfig;
import io.quarkiverse.langchain4j.openai.runtime.devui.OpenAiImagesJsonRPCService;
import io.quarkiverse.langchain4j.openai.runtime.devui.OpenAiModerationModelsJsonRPCService;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.devui.spi.JsonRPCProvidersBuildItem;
import io.quarkus.devui.spi.page.CardPageBuildItem;
import io.quarkus.devui.spi.page.Page;

public class OpenAiDevUIProcessor {

    @BuildStep(onlyIf = IsDevelopment.class)
    CardPageBuildItem cardPage(
            LangChain4jOpenAiBuildConfig config,
            List<SelectedImageModelProviderBuildItem> imageModels,
            List<SelectedModerationModelProviderBuildItem> moderationModels) {
        CardPageBuildItem card = new CardPageBuildItem();
        addImageModelPage(config, card, imageModels);
        addModerationModelPage(config, card, moderationModels);
        return card;
    }

    @BuildStep(onlyIf = IsDevelopment.class)
    void jsonRpcProviders(BuildProducer<JsonRPCProvidersBuildItem> rpcProviders,
            LangChain4jOpenAiBuildConfig config) {
        if (config.imageModel().enabled().orElse(true)) {
            rpcProviders.produce(new JsonRPCProvidersBuildItem(OpenAiImagesJsonRPCService.class));
        }
        if (config.moderationModel().enabled().orElse(true)) {
            rpcProviders.produce(new JsonRPCProvidersBuildItem(OpenAiModerationModelsJsonRPCService.class));
        }
    }

    private void addImageModelPage(
            LangChain4jOpenAiBuildConfig config,
            CardPageBuildItem card,
            List<SelectedImageModelProviderBuildItem> imageModels) {
        List<String> configurations = new ArrayList<>();
        for (SelectedImageModelProviderBuildItem imageModel : imageModels) {
            configurations.add(imageModel.getConfigName());
        }
        if (configurations.isEmpty()) {
            // Even if the default OpenAI config instance is not injected
            // anywhere, let the image page know about it so users can play
            // around with images out of the box (even with an empty
            // application). If the API key isn't configured, don't fail the
            // startup - the image page will show an error.
            configurations.add(NamedConfigUtil.DEFAULT_NAME);
        }
        if (config.imageModel().enabled().orElse(true)) {
            card.addBuildTimeData("imageModelConfigurations", configurations);
            card.addPage(Page.webComponentPageBuilder().title("Images")
                    .componentLink("qwc-images.js")
                    .icon("font-awesome-solid:palette"));
        }
    }

    private void addModerationModelPage(
            LangChain4jOpenAiBuildConfig config,
            CardPageBuildItem card,
            List<SelectedModerationModelProviderBuildItem> moderationModels) {
        List<String> configurations = new ArrayList<>();
        for (SelectedModerationModelProviderBuildItem imageModel : moderationModels) {
            configurations.add(imageModel.getConfigName());
        }
        if (configurations.isEmpty()) {
            configurations.add(NamedConfigUtil.DEFAULT_NAME);
        }
        if (config.moderationModel().enabled().orElse(true)) {
            card.addBuildTimeData("moderationModelConfigurations", configurations);
            card.addPage(Page.webComponentPageBuilder().title("Moderation model")
                    .componentLink("qwc-moderation.js")
                    .icon("font-awesome-solid:triangle-exclamation"));
        }
    }

}
