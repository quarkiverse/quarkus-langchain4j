package io.quarkiverse.langchain4j.openai.runtime.utils;

import io.netty.util.internal.StringUtil;
import io.quarkiverse.langchain4j.openai.runtime.config.LangChain4jOpenAiConfig;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;

public class ValidationUtil {

    private static final String OPENAI_BASE_URL = "https://api.openai.com/v1/";

    public static void isValidDefaultConfig(String configuration,
            LangChain4jOpenAiConfig config) {
        String defaultAPiKey = config.defaultConfig().apiKey().orElse(StringUtil.EMPTY_STRING);
        if (NamedConfigUtil.isDefault(configuration) && StringUtil.isNullOrEmpty(defaultAPiKey)
                && OPENAI_BASE_URL.equals(config.defaultConfig().baseUrl())) {
            // for non-default providers, we assume that Quarkus has verified by now that the api key is set
            throw new RuntimeException("OpenAI API key is not configured. " +
                    "Please specify the key in the `quarkus.langchain4j.openai.api-key` configuration property.");
        }
    }

    private ValidationUtil() {
    }
}
