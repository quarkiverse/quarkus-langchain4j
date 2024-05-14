package io.quarkiverse.langchain4j.deployment.items;

public interface DevServicesModelRequired {

    String getProvider();

    String getModelName();

    String getBaseUrlProperty();
}
