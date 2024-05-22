package io.quarkiverse.langchain4j.openai.runtime.devui;

import java.time.Duration;
import java.util.Optional;

import jakarta.inject.Inject;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.model.image.ImageModel;
import io.netty.util.internal.StringUtil;
import io.quarkiverse.langchain4j.openai.QuarkusOpenAiImageModel;
import io.quarkiverse.langchain4j.openai.runtime.config.LangChain4jOpenAiConfig;
import io.quarkiverse.langchain4j.openai.runtime.utils.ValidationUtil;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.vertx.core.json.JsonObject;

public class OpenAiImagesJsonRPCService {

    @Inject
    LangChain4jOpenAiConfig config;

    public JsonObject generate(String configuration, String modelName, String size, String prompt, String quality) {
        ValidationUtil.isValidDefaultConfig(configuration, config);
        LangChain4jOpenAiConfig.OpenAiConfig clientConfig = NamedConfigUtil.isDefault(configuration) ? config.defaultConfig()
                : config.namedConfig().get(configuration);
        ImageModel model = QuarkusOpenAiImageModel.builder()
                .baseUrl(clientConfig.baseUrl())
                .apiKey(clientConfig.apiKey().orElse(StringUtil.EMPTY_STRING))
                .timeout(clientConfig.timeout().orElse(Duration.ofSeconds(10)))
                .user(clientConfig.imageModel().user())
                .maxRetries(clientConfig.maxRetries())
                .persistDirectory(Optional.empty())
                .modelName(modelName)
                .quality(quality)
                .size(size)
                .build();
        Image image = model.generate(prompt).content();
        JsonObject result = new JsonObject();
        result.put("prompt", prompt);
        // there's either URL or base64Data present in the response, depending
        // on `quarkus.langchain4j.openai.image-model.response-format`
        if (image.url() != null) {
            result.put("url", image.url().toString());
        } else {
            result.put("url", null);
        }
        if (image.base64Data() != null && !image.base64Data().isEmpty()) {
            result.put("base64Data", image.base64Data());
        } else {
            result.put("base64Data", null);
        }
        return result;
    }

}
