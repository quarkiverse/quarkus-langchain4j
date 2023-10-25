package io.quarkiverse.langchain4j.runtime.graalvm;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

import dev.langchain4j.data.message.ChatMessageJsonCodec;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.internal.Json;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.input.structured.StructuredPromptProcessor;
import dev.langchain4j.service.AiServiceContext;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.spi.prompt.PromptTemplateFactory;
import dev.langchain4j.spi.prompt.structured.StructuredPromptFactory;
import io.quarkiverse.langchain4j.QuarkusAiServicesFactory;
import io.quarkiverse.langchain4j.QuarkusChatMessageJsonCodecFactory;
import io.quarkiverse.langchain4j.QuarkusJsonCodecFactory;
import io.quarkiverse.langchain4j.QuarkusPromptTemplateFactory;
import io.quarkiverse.langchain4j.QuarkusStructuredPromptFactory;

public class Substitutions {

    @TargetClass(PromptTemplate.class)
    static final class Target_PromptTemplate {

        @Substitute
        private static PromptTemplateFactory factory() {
            return new QuarkusPromptTemplateFactory();
        }
    }

    @TargetClass(StructuredPromptProcessor.class)
    static final class Target_StructuredPromptProcessor {

        @Substitute
        private static StructuredPromptFactory factory() {
            return new QuarkusStructuredPromptFactory();
        }
    }

    @TargetClass(AiServices.class)
    static final class Target_AiServices {

        @Substitute
        public static <T> AiServices<T> builder(Class<T> aiService) {
            return new QuarkusAiServicesFactory.QuarkusAiServices<>(new AiServiceContext(aiService));
        }
    }

    @TargetClass(Json.class)
    static final class Target_Json {

        @Substitute
        private static Json.JsonCodec loadCodec() {
            return new QuarkusJsonCodecFactory().create();
        }
    }

    @TargetClass(ChatMessageSerializer.class)
    static final class Target_ChatMessageSerializer {

        @Substitute
        private static ChatMessageJsonCodec loadCodec() {
            return new QuarkusChatMessageJsonCodecFactory().create();
        }
    }
}
