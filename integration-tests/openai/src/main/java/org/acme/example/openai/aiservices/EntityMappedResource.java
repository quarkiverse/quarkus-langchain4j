package org.acme.example.openai.aiservices;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

import org.jboss.resteasy.reactive.RestQuery;

import com.fasterxml.jackson.annotation.JsonProperty;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.structured.Description;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@Path("collection-entity-mapping")
public class EntityMappedResource {

    private final EntityMappedDescriber describer;

    public EntityMappedResource(EntityMappedDescriber describer) {
        this.describer = describer;
    }

    public static class TestData {
        @Description("Foo description for structured output")
        @JsonProperty("foo")
        String foo;

        @Description("Foo description for structured output")
        @JsonProperty("bar")
        Integer bar;

        @Description("Foo description for structured output")
        @JsonProperty("baz")
        Optional<Double> baz;

        public TestData() {
        }

        TestData(String foo, Integer bar, Double baz) {
            this.foo = foo;
            this.bar = bar;
            this.baz = Optional.of(baz);
        }
    }

    public static class MirrorModelSupplier implements Supplier<ChatLanguageModel> {
        @Override
        public ChatLanguageModel get() {
            return (messages) -> new Response<>(new AiMessage("""
                    [
                        {
                            "foo": "asd",
                            "bar": 1,
                            "baz": 2.0
                        }
                    ]
                    """));
        }
    }

    @POST
    @Path("generateMapped")
    public List<TestData> generateMapped(@RestQuery String message) {
        List<TestData> inputs = new ArrayList<>();
        inputs.add(new TestData(message, 100, 100.0));

        var test = describer.describeMapped(inputs);
        return test;
    }

    @RegisterAiService(chatLanguageModelSupplier = MirrorModelSupplier.class)
    public interface EntityMappedDescriber {

        @UserMessage("This is a describer returning a collection of mapped entities")
        List<TestData> describeMapped(List<TestData> inputs);
    }
}
