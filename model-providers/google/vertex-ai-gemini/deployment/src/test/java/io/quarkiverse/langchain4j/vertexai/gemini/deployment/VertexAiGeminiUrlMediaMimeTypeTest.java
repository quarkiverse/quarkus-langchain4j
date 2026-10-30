package io.quarkiverse.langchain4j.vertexai.gemini.deployment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.data.audio.Audio;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.AudioContent;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.PdfFileContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.VideoContent;
import dev.langchain4j.data.video.Video;
import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.langchain4j.auth.ModelAuthProvider;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

public class VertexAiGeminiUrlMediaMimeTypeTest extends WiremockAware {

    private static final String CHAT_MODEL_ID = "gemini-3-flash-preview";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.vertexai.gemini.base-url", WiremockAware.wiremockUrlForConfig())
            .overrideRuntimeConfigKey("quarkus.langchain4j.vertexai.gemini.chat-model.model-id", CHAT_MODEL_ID);

    @Inject
    ChatModel chatModel;

    @BeforeEach
    void setUp() {
        resetRequests();
        wiremock().register(
                post(urlPathEqualTo(String.format(
                        "/v1/projects/dummy/locations/dummy/publishers/google/models/%s:generateContent", CHAT_MODEL_ID)))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody(
                                        """
                                                {"candidates":[{"content":{"role":"model","parts":[{"text":"Done."}]},"finishReason":"STOP"}],
                                                 "usageMetadata":{"promptTokenCount":1,"candidatesTokenCount":1,"totalTokenCount":2}}
                                                """)));
    }

    @Test
    void urlMediaWithoutExtensionIsSentWithItsMimeType() throws Exception {
        chatModel.chat(UserMessage.from(
                TextContent.from("Describe these files."),
                ImageContent.from(Image.builder().url(URI.create("gs://bucket/photo")).mimeType("image/png").build()),
                PdfFileContent.from(URI.create("gs://bucket/report")),
                VideoContent.from(Video.builder().url(URI.create("gs://bucket/clip")).mimeType("video/mp4").build()),
                AudioContent.from(Audio.builder().url(URI.create("gs://bucket/voice")).mimeType("audio/mpeg").build())));

        assertThat(sentParts()).isEqualTo(MAPPER.readTree("""
                [
                  {"text":"Describe these files."},
                  {"fileData":{"mimeType":"image/png","fileUri":"gs://bucket/photo"}},
                  {"fileData":{"mimeType":"application/pdf","fileUri":"gs://bucket/report"}},
                  {"fileData":{"mimeType":"video/mp4","fileUri":"gs://bucket/clip"}},
                  {"fileData":{"mimeType":"audio/mpeg","fileUri":"gs://bucket/voice"}}
                ]
                """));
    }

    @Test
    void urlMediaWithoutMimeTypeFallsBackToTheFileExtension() throws Exception {
        chatModel.chat(UserMessage.from(
                TextContent.from("Describe this image."),
                ImageContent.from(URI.create("gs://bucket/photo.png"))));

        assertThat(sentParts().get(1)).isEqualTo(MAPPER.readTree("""
                {"fileData":{"mimeType":"image/png","fileUri":"gs://bucket/photo.png"}}
                """));
    }

    private JsonNode sentParts() throws Exception {
        return MAPPER.readTree(singleLoggedRequest().getBodyAsString()).get("contents").get(0).get("parts");
    }

    @Singleton
    public static class DummyAuthProvider implements ModelAuthProvider {

        @Override
        public String getAuthorization(Input input) {
            return "Bearer somekey";
        }
    }
}
