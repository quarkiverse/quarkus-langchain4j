package io.quarkiverse.langchain4j.ollama.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.model.output.structured.Description;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;

public class OllamaJsonOutputTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideConfigKey("quarkus.langchain4j.devservices.enabled", "false")
            .overrideConfigKey("quarkus.langchain4j.ollama.chat-model.format", "json");

    @Description("A person")
    public record Person(
            @Description("The firstname") String firstname,
            @Description("The lastname") String lastname) {
    }

    @Singleton
    @RegisterAiService
    interface AiService {
        Person extractPerson(String text);
    }

    @Inject
    AiService aiService;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void reset() {
        mock.reset();
    }

    @Test
    void extract() throws Exception {
        mock.chat().reply("{\"firstname\":\"Alan\",\"lastname\":\"Wake\"}");

        var result = aiService.extractPerson("Tell me something about Alan Wake");
        assertEquals(new Person("Alan", "Wake"), result);

        CapturedRequest request = mock.chat().singleRequest();
        assertThat(request.path()).isEqualTo("/ollama/api/chat");
        assertThat(request.bodyAsJson()).isEqualTo(new ObjectMapper().readTree(
                """
                        {
                            "model": "llama3.2",
                            "messages": [
                                {
                                    "role": "user",
                                    "content": "Tell me something about Alan Wake\\nYou must answer strictly in the following JSON format: {\\n\\\"firstname\\\": (The firstname; type: string),\\n\\\"lastname\\\": (The lastname; type: string)\\n}"
                                }
                            ],
                            "options": {
                                "temperature": 0.8,
                                "top_k": 40,
                                "top_p": 0.9,
                                "stop" : [ ]
                            },
                            "format": "json",
                            "stream": false,
                            "tools" : [ ]
                        }"""));
    }
}
