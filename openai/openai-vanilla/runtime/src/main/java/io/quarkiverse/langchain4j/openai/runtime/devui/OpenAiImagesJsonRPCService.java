package io.quarkiverse.langchain4j.openai.runtime.devui;

import java.util.Optional;

import jakarta.inject.Inject;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.model.image.ImageModel;
import io.quarkiverse.langchain4j.openai.QuarkusOpenAiImageModel;
import io.quarkiverse.langchain4j.openai.runtime.config.LangChain4jOpenAiConfig;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.vertx.core.json.JsonObject;

public class OpenAiImagesJsonRPCService {

    @Inject
    LangChain4jOpenAiConfig config;

    public JsonObject generate(String configuration, String modelName, String size, String prompt, String quality) {
        if (NamedConfigUtil.isDefault(configuration) && config.defaultConfig().apiKey().equals("dummy")) {
            // for non-default providers, we assume that Quarkus has verified by now that the api key is set
            throw new RuntimeException("OpenAI API key is not configured. " +
                    "Please specify the key in the `quarkus.langchain4j.openai.api-key` configuration property.");
        }
        LangChain4jOpenAiConfig.OpenAiConfig clientConfig = NamedConfigUtil.isDefault(configuration) ? config.defaultConfig()
                : config.namedConfig().get(configuration);
        ImageModel model = QuarkusOpenAiImageModel.builder()
                .baseUrl(clientConfig.baseUrl())
                .apiKey(clientConfig.apiKey())
                .timeout(clientConfig.timeout())
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
