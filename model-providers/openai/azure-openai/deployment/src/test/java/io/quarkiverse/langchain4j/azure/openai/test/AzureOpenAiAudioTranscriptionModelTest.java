package io.quarkiverse.langchain4j.azure.openai.test;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.audio.Audio;
import dev.langchain4j.model.audio.AudioTranscriptionModel;
import dev.langchain4j.model.audio.AudioTranscriptionRequest;
import dev.langchain4j.model.audio.AudioTranscriptionResponse;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;

public class AzureOpenAiAudioTranscriptionModelTest {

    private static final String TOKEN = "whatever";

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.azure-openai.api-key", TOKEN);

    @Inject
    AudioTranscriptionModel audioTranscriptionModel;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void reset() {
        mock.reset();
    }

    @Test
    public void transcribesAudioViaMultipartRequest() {
        // the mock does not render transcription replies: the recorded OpenAI response is served as is
        mock.raw().on("POST", "/openai/deployments/mock-deployment/audio/transcriptions")
                .reply(200, "{\"text\":\"Hello, world!\"}");

        AudioTranscriptionRequest request = AudioTranscriptionRequest.builder()
                .audio(Audio.builder().binaryData(new byte[] { 1, 2, 3 }).mimeType("audio/mpeg").build())
                .build();

        AudioTranscriptionResponse response = audioTranscriptionModel.transcribe(request);

        assertThat(response.text()).isEqualTo("Hello, world!");

        assertThat(mock.requests()).hasSize(1);
        CapturedRequest capturedRequest = mock.requests().get(0);
        assertThat(capturedRequest.path()).isEqualTo("/openai/deployments/mock-deployment/audio/transcriptions");
        assertThat(capturedRequest.query()).contains("api-version=2024-10-21");
        assertThat(capturedRequest.header("Content-Type")).startsWith("multipart/form-data");
        assertThat(capturedRequest.header("User-Agent")).isEqualTo("langchain4j-quarkus-azure-openai");
        assertThat(capturedRequest.header("api-key")).isEqualTo(TOKEN);

        // the multipart body is kept byte for byte, as ISO-8859-1 text
        assertThat(capturedRequest.body())
                .contains("name=\"model\"")
                .contains("whisper-1")
                .contains("name=\"file\"")
                .contains("filename=\"audio_file.mp3\"")
                .contains("content-type: audio/mpeg")
                .contains("\u0001\u0002\u0003");
    }
}
