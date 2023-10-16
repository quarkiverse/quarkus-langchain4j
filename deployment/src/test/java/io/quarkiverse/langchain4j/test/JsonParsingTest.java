package io.quarkiverse.langchain4j.test;

import static org.assertj.core.api.Assertions.assertThat;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.ai4j.openai4j.chat.ChatCompletionResponse;
import io.quarkiverse.langchain4j.QuarkusRestApi;
import io.quarkus.test.QuarkusUnitTest;

public class JsonParsingTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class));

    @Test
    void testChatCompletion() throws JsonProcessingException {
        ObjectMapper mapperToUse = QuarkusRestApi.OpenAiRestApiJacksonProvider.ObjectMapperHolder.MAPPER;

        ChatCompletionResponse chatCompletionResponse = mapperToUse.readValue(
                "{\"id\":\"chatcmpl-8AAeH0Sdve2wfHWhFIXq1gFkkqoIU\",\"object\":\"chat.completion.chunk\",\"created\":1697434905,\"model\":\"gpt-3.5-turbo-0613\",\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"length\"}]}",
                ChatCompletionResponse.class);

        assertThat(chatCompletionResponse.choices()).singleElement().satisfies(c -> {
            assertThat(c.finishReason()).isEqualTo("length");
        });
    }
}
