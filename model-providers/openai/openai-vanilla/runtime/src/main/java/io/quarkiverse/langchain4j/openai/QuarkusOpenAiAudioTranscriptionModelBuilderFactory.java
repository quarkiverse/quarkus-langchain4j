package io.quarkiverse.langchain4j.openai;

import java.net.Proxy;

import dev.langchain4j.model.openai.OpenAiAudioTranscriptionModel;
import dev.langchain4j.model.openai.spi.OpenAiAudioTranscriptionModelBuilderFactory;
import io.quarkiverse.langchain4j.openai.common.runtime.AdditionalPropertiesHack;

public class QuarkusOpenAiAudioTranscriptionModelBuilderFactory implements OpenAiAudioTranscriptionModelBuilderFactory {

    @Override
    public OpenAiAudioTranscriptionModel.Builder get() {
        return new Builder();
    }

    public static class Builder extends OpenAiAudioTranscriptionModel.Builder {

        private String configName;
        private String tlsConfigurationName;
        private Proxy proxy;

        public Builder configName(String configName) {
            this.configName = configName;
            return this;
        }

        public Builder tlsConfigurationName(String tlsConfigurationName) {
            this.tlsConfigurationName = tlsConfigurationName;
            return this;
        }

        public Builder proxy(Proxy proxy) {
            this.proxy = proxy;
            return this;
        }

        @Override
        public OpenAiAudioTranscriptionModel build() {
            AdditionalPropertiesHack.setConfigName(configName);
            AdditionalPropertiesHack.setTlsConfigurationName(tlsConfigurationName);
            AdditionalPropertiesHack.setProxy(proxy);
            return super.build();
        }
    }
}
