package io.quarkiverse.langchain4j.chatscopes.passivation.deployment;

import io.quarkiverse.langchain4j.chatscopes.passivation.FileChatScopeStore;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;

public class FilePassivationProcessor {
    @BuildStep
    public void chatScopeStore(BuildProducer<AdditionalBeanBuildItem> additionalBeanProducer) {

        additionalBeanProducer
                .produce(AdditionalBeanBuildItem.builder().addBeanClass(FileChatScopeStore.class).setUnremovable()
                        .build());
    }

}
