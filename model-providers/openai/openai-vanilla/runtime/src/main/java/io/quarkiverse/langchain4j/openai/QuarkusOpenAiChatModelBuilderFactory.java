package io.quarkiverse.langchain4j.openai;

import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.spi.OpenAiChatModelBuilderFactory;
import io.quarkiverse.langchain4j.openai.common.runtime.AdditionalPropertiesHack;

public class QuarkusOpenAiChatModelBuilderFactory implements OpenAiChatModelBuilderFactory {
    @Override
    public OpenAiChatModel.OpenAiChatModelBuilder get() {
        return new Builder();
    }

    public static class Builder extends OpenAiChatModel.OpenAiChatModelBuilder {

        private String tlsConfigurationName;

        public Builder tlsConfigurationName(String tlsConfigurationName) {
            this.tlsConfigurationName = tlsConfigurationName;
            return this;
        }

        @Override
        public OpenAiChatModel build() {
            AdditionalPropertiesHack.setTlsConfigurationName(tlsConfigurationName);
            return super.build();
        }
    }
}
