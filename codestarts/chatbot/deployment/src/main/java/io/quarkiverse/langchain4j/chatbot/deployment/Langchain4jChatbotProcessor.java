package io.quarkiverse.langchain4j.chatbot.deployment;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

class Langchain4jChatbotProcessor {

    private static final String FEATURE = "langchain4j-chatbot";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }
}
