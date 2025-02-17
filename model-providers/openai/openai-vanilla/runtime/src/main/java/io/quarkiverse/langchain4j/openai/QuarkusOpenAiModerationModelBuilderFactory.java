package io.quarkiverse.langchain4j.openai;

import dev.langchain4j.model.openai.OpenAiModerationModel;
import dev.langchain4j.model.openai.spi.OpenAiModerationModelBuilderFactory;
import io.quarkiverse.langchain4j.openai.common.runtime.AdditionalPropertiesHack;

public class QuarkusOpenAiModerationModelBuilderFactory implements OpenAiModerationModelBuilderFactory {

    @Override
    public OpenAiModerationModel.OpenAiModerationModelBuilder get() {
        return new Builder();
    }

    public static class Builder extends OpenAiModerationModel.OpenAiModerationModelBuilder {

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
        public OpenAiModerationModel build() {
            AdditionalPropertiesHack.setConfigName(configName);
            AdditionalPropertiesHack.setTlsConfigurationName(tlsConfigurationName);
            return super.build();
        }
    }
}
