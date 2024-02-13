package io.quarkiverse.langchain4j.azure.openai.test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;

import io.quarkus.bootstrap.classloading.QuarkusClassLoader;

public class WiremockUtils {

    private static final String DEFAULT_CHAT_MESSAGE_CONTENT = "Hello there, how may I assist you today?";
    private static final String CHAT_MESSAGE_CONTENT_TEMPLATE;
    private static final String DEFAULT_CHAT_RESPONSE_BODY;
    public static final ResponseDefinitionBuilder CHAT_RESPONSE_WITHOUT_BODY;
    private static final ResponseDefinitionBuilder DEFAULT_CHAT_RESPONSE;

    static {
        try (InputStream is = getClassLoader().getResourceAsStream("chat/default.json")) {
            CHAT_MESSAGE_CONTENT_TEMPLATE = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            DEFAULT_CHAT_RESPONSE_BODY = String.format(CHAT_MESSAGE_CONTENT_TEMPLATE, DEFAULT_CHAT_MESSAGE_CONTENT);
            CHAT_RESPONSE_WITHOUT_BODY = aResponse().withHeader("Content-Type", "application/json");
            DEFAULT_CHAT_RESPONSE = CHAT_RESPONSE_WITHOUT_BODY
                    .withBody(DEFAULT_CHAT_RESPONSE_BODY);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static ClassLoader getClassLoader() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        while (loader instanceof QuarkusClassLoader) {
            loader = loader.getParent();
        }
        return loader;
    }

    private static ResponseDefinitionBuilder defaultChatCompletionResponse() {
        return DEFAULT_CHAT_RESPONSE;
    }

    public static MappingBuilder chatCompletionMapping(String token) {
        return post(urlEqualTo("/v1/chat/completions?api-version=2023-05-15"))
                .withHeader("api-key", equalTo(token));
    }

    public static MappingBuilder defaultChatCompletionsStub(String token) {
        return chatCompletionMapping(token)
                .willReturn(defaultChatCompletionResponse());
    }

}
