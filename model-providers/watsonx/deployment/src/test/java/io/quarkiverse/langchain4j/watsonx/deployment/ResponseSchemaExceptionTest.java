package io.quarkiverse.langchain4j.watsonx.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.input.structured.StructuredPrompt;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.test.QuarkusUnitTest;

public class ResponseSchemaExceptionTest {

    private static final String MESSAGE = "The {response_schema} placeholder cannot be used if the property quarkus.langchain4j.response-schema is set to false. Found in: io.quarkiverse.langchain4j.watsonx.deployment.ResponseSchemaExceptionTest$";

    @StructuredPrompt("{response_schema} Create a poem about {topic}")
    static class PoemPrompt {

        private final String topic;

        public PoemPrompt(String topic) {
            this.topic = topic;
        }

        public String getTopic() {
            return topic;
        }
    }

    @RegisterAiService
    @Singleton
    interface AiServiceSystemMessage {

        @SystemMessage("{response_schema} You are a poet")
        @UserMessage("Generate a poem about {topic}")
        String poem(String topic);
    }

    @RegisterAiService
    @Singleton
    interface AiServiceUserMessage {

        @SystemMessage("You are a poet")
        @UserMessage("{response_schema} Generate a poem about {topic}")
        String poem(String topic);
    }

    @RegisterAiService
    @Singleton
    @SystemMessage("{response_schema} You are a poet")
    interface AiServiceSystemMessageOnClass {

        @UserMessage("Generate a poem about {topic}")
        String poem(String topic);
    }

    @RegisterAiService
    @Singleton
    interface AiServiceStructuredPrompt {
        String poem(PoemPrompt prompt);
    }

    @RegisterAiService
    @Singleton
    interface AIServiceOnMethod {
        String poem(@UserMessage String message, @V("topic") String topic);
    }

    @Nested
    class ResponseSchemaSystemMessage {
        @RegisterExtension
        static QuarkusUnitTest unitTest = new QuarkusUnitTest()
                .overrideConfigKey("quarkus.langchain4j.response-schema", "false")
                .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(AiServiceSystemMessage.class))
                .assertException(t -> {
                    assertThat(t).isInstanceOf(RuntimeException.class)
                            .hasMessage(MESSAGE.concat("AiServiceSystemMessage"));
                });

        @Test
        void test() {
            fail("Should not be called");
        }
    }

    @Nested
    class ResponseSchemaUserMessage {
        @RegisterExtension
        static QuarkusUnitTest unitTest = new QuarkusUnitTest()
                .overrideConfigKey("quarkus.langchain4j.response-schema", "false")
                .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(AiServiceUserMessage.class))
                .assertException(t -> {
                    assertThat(t).isInstanceOf(RuntimeException.class)
                            .hasMessage(MESSAGE.concat("AiServiceUserMessage"));
                });

        @Test
        void test() {
            fail("Should not be called");
        }
    }

    @Nested
    class ResponseSchemaStructuredPrompt {
        @RegisterExtension
        static QuarkusUnitTest unitTest = new QuarkusUnitTest()
                .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClasses(AiServiceStructuredPrompt.class,
                        PoemPrompt.class))
                .assertException(t -> {
                    assertThat(t).isInstanceOf(RuntimeException.class)
                            .hasMessage(
                                    "The {response_schema} placeholder is not enabled for the @StructuredPrompt. Found it: io.quarkiverse.langchain4j.watsonx.deployment.ResponseSchemaExceptionTest$PoemPrompt");
                });

        @Test
        void test() {
            fail("Should not be called");
        }
    }

    @Nested
    class ResponseSchemaSystemMessageOnClass {
        @RegisterExtension
        static QuarkusUnitTest unitTest = new QuarkusUnitTest()
                .overrideConfigKey("quarkus.langchain4j.response-schema", "false")
                .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(AiServiceSystemMessageOnClass.class))
                .assertException(t -> {
                    assertThat(t).isInstanceOf(RuntimeException.class)
                            .hasMessage(MESSAGE.concat("AiServiceSystemMessageOnClass"));
                });

        @Test
        void test() {
            fail("Should not be called");
        }
    }
}
