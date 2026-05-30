package io.quarkiverse.langchain4j.azure.openai.test;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import dev.langchain4j.data.audio.Audio;
import dev.langchain4j.model.audio.AudioTranscriptionModel;
import dev.langchain4j.model.audio.AudioTranscriptionRequest;
import dev.langchain4j.model.audio.AudioTranscriptionResponse;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

public class AzureOpenAiAudioTranscriptionModelTest extends OpenAiBaseTest {

    private static final String TOKEN = "whatever";

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.azure-openai.api-key", TOKEN)
            .overrideRuntimeConfigKey("quarkus.langchain4j.azure-openai.endpoint",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    @Inject
    AudioTranscriptionModel audioTranscriptionModel;

    @Test
    public void transcribesAudioViaMultipartRequest() {
        AudioTranscriptionRequest request = AudioTranscriptionRequest.builder()
                .audio(Audio.builder().binaryData(new byte[] { 1, 2, 3 }).mimeType("audio/mpeg").build())
                .build();

        AudioTranscriptionResponse response = audioTranscriptionModel.transcribe(request);

        assertThat(response.text()).isEqualTo("Hello, world!");

        LoggedRequest loggedRequest = singleLoggedRequest();
        assertThat(loggedRequest.getUrl())
                .startsWith("/v1/audio/transcriptions")
                .contains("api-version=2024-10-21");
        assertThat(loggedRequest.getHeader("Content-Type")).startsWith("multipart/form-data");
        assertThat(loggedRequest.getHeader("User-Agent")).isEqualTo("langchain4j-quarkus-azure-openai");

        String body = new String(requestBodyOfSingleRequest(), UTF_8);
        assertThat(body)
                .contains("name=\"model\"")
                .contains("whisper-1")
                .contains("name=\"file\"")
                .contains("filename=\"audio_file.mp3\"")
                .contains("content-type: audio/mpeg");
    }
}
