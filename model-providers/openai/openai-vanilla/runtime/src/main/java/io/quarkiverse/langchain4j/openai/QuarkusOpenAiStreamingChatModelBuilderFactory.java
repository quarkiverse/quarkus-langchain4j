package io.quarkiverse.langchain4j.openai;

import java.net.Proxy;

import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.openai.spi.OpenAiStreamingChatModelBuilderFactory;
import io.quarkiverse.langchain4j.openai.common.runtime.AdditionalPropertiesHack;

public class QuarkusOpenAiStreamingChatModelBuilderFactory implements OpenAiStreamingChatModelBuilderFactory {

    @Override
    public OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder get() {
        return new Builder();
    }

    public static class Builder extends OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder {

        private String configName;
        private String tlsConfigurationName;
        private boolean logCurl;

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

        public Builder logCurl(boolean logCurl) {
            this.logCurl = logCurl;
            return this;
        }

        @Override
        public OpenAiStreamingChatModel build() {
            AdditionalPropertiesHack.setConfigName(configName);
            AdditionalPropertiesHack.setTlsConfigurationName(tlsConfigurationName);
            AdditionalPropertiesHack.setLogCurl(logCurl);
            return super.build();
        }
    }
}
