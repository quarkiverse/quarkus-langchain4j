package org.acme.example.multiple;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.azure.openai.AzureOpenAiEmbeddingModel;
import io.quarkiverse.langchain4j.ollama.OllamaEmbeddingModel;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class MultipleEmbeddingModelsTest {

    @Inject
    @ModelName("e1")
    EmbeddingModel firstNamedModel;

    @Inject
    @ModelName("e2")
    EmbeddingModel secondNamedModel;

    @Inject
    @ModelName("e3")
    EmbeddingModel fifthNamedModel;

    @Inject
    @ModelName("c1")
    EmbeddingModel thirdNamedModel;

    @Inject
    @ModelName("c2")
    EmbeddingModel fourthNamedModel;

    @Inject
    @Any
    Instance<EmbeddingModel> anyModel;

    @Test
    void firstNamedModel() {
        assertThat(SubclassUtil.unwrap(firstNamedModel)).isInstanceOf(OpenAiEmbeddingModel.class);
    }

    @Test
    void secondNamedModel() {
        assertThat(ClientProxy.unwrap(secondNamedModel)).isInstanceOf(OllamaEmbeddingModel.class);
    }

    @Test
    void thirdNamedModel() {
        assertThat(ClientProxy.unwrap(thirdNamedModel)).isInstanceOf(AzureOpenAiEmbeddingModel.class);
    }

    @Test
    void fourthNamedModel() {
        assertThat(ClientProxy.unwrap(fourthNamedModel)).isInstanceOf(AzureOpenAiEmbeddingModel.class);
    }

    @Test
    void fifthNamedModel() {
        assertThat(ClientProxy.unwrap(fifthNamedModel)).isInstanceOf(AllMiniLmL6V2EmbeddingModel.class);
    }

    @Test
    void anyInstanceInjection() {
        // e4 model has no direct injection point; we only request it via Instance<T>
        assertThat(anyModel.handlesStream()
                .anyMatch(handle -> handle.getBean().getQualifiers().contains(ModelName.Literal.of("e4")))).isTrue();
    }
}
