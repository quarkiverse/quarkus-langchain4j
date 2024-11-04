package org.acme.example.openai.aiservices;

import java.util.ArrayList;
import java.util.List;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

import org.jboss.resteasy.reactive.RestQuery;

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
        String foo;

        @Description("Foo description for structured output")
        Integer bar;

        @Description("Foo description for structured output")
        Double baz;

        TestData(String foo, Integer bar, Double baz) {
            this.foo = foo;
            this.bar = bar;
            this.baz = baz;
        }
    }

    @POST
    public List<String> generate(@RestQuery String message) {
        var result = describer.describe(message);

        return result;
    }

    @POST
    @Path("generateMapped")
    public List<TestData> generateMapped(@RestQuery String message) {
        List<TestData> inputs = new ArrayList<>();
        inputs.add(new TestData(message, 100, 100.0));

        return describer.describeMapped(inputs);
    }

    @RegisterAiService
    public interface EntityMappedDescriber {

        @UserMessage("This is a describer returning a collection of strings")
        List<String> describe(String url);

        @UserMessage("This is a describer returning a collection of mapped entities")
        List<TestData> describeMapped(List<TestData> inputs);
    }
}
