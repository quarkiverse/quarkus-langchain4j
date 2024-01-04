package io.quarkiverse.langchain4j.openai.runtime.devui;

import java.time.Duration;
import java.util.Optional;

import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.model.image.ImageModel;
import io.quarkiverse.langchain4j.openai.QuarkusOpenAiImageModel;
import io.vertx.core.json.JsonObject;

public class OpenAiImagesJsonRPCService {

    @Inject
    @ConfigProperty(name = "quarkus.langchain4j.openai.base-url")
    String baseUrl;

    @Inject
    @ConfigProperty(name = "quarkus.langchain4j.openai.api-key")
    String apiKey;

    @Inject
    @ConfigProperty(name = "quarkus.langchain4j.openai.timeout")
    Duration timeout;

    @Inject
    @ConfigProperty(name = "quarkus.langchain4j.openai.image-model.user")
    Optional<String> user;

    @Inject
    @ConfigProperty(name = "quarkus.langchain4j.openai.max-retries")
    Integer maxRetries;

    public JsonObject generate(String modelName, String size, String prompt, String quality) {
        ImageModel model = QuarkusOpenAiImageModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .timeout(timeout)
                .user(user)
                .maxRetries(maxRetries)
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
