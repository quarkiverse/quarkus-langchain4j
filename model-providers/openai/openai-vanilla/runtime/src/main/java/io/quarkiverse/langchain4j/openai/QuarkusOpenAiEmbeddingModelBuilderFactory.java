package io.quarkiverse.langchain4j.openai;

import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.spi.OpenAiEmbeddingModelBuilderFactory;
import io.quarkiverse.langchain4j.openai.common.runtime.AdditionalPropertiesHack;

public class QuarkusOpenAiEmbeddingModelBuilderFactory implements OpenAiEmbeddingModelBuilderFactory {

    @Override
    public OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder get() {
        return new Builder();
    }

    public static class Builder extends OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder {

        private String configName;
        private String tlsConfigurationName;

        public Builder configName(String configName) {
            this.configName = configName;
            return this;
        }

        public Builder tlsConfigurationName(String tlsConfigurationName) {
            this.tlsConfigurationName = tlsConfigurationName;
            return this;
        }

        @Override
        public OpenAiEmbeddingModel build() {
            AdditionalPropertiesHack.setConfigName(configName);
            AdditionalPropertiesHack.setTlsConfigurationName(tlsConfigurationName);
            return super.build();
        }
    }
}
