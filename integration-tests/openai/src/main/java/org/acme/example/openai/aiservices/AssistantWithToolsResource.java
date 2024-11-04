package org.acme.example.openai.aiservices;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Singleton;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import org.jboss.resteasy.reactive.RestQuery;

import com.fasterxml.jackson.annotation.JsonProperty;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.output.structured.Description;
import io.quarkiverse.langchain4j.RegisterAiService;

@Path("assistant-with-tool")
public class AssistantWithToolsResource {

    private final Assistant assistant;

    public AssistantWithToolsResource(Assistant assistant) {
        this.assistant = assistant;
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

    @GET
    public String get(@RestQuery String message) {
        return assistant.chat(message);
    }

    @RegisterAiService(tools = Calculator.class, chatMemoryProviderSupplier = RegisterAiService.BeanChatMemoryProviderSupplier.class)
    public interface Assistant {

        String chat(String userMessage);

    }

    @Singleton
    public static class Calculator {

        @Tool("Calculates the length of a string")
        int stringLength(String s) {
            return s.length();
        }

        @Tool("Calculates the sum of two numbers")
        int add(int a, int b) {
            return a + b;
        }

        @Tool("Calculates the square root of a number")
        double sqrt(int x) {
            return Math.sqrt(x);
        }

        @Tool("Calculates the the sum of all provided numbers")
        double sumAll(List<Double> x) {

            return x.stream().reduce(0.0, (a, b) -> a + b);
        }

        @Tool("Evaluate test data object")
        public TestData evaluateTestObject(List<TestData> data) {
            return new TestData("Empty", 0, 0.0);
        }

        @Tool("Calculates all factors of the provided integer.")
        List<Integer> getFactors(int x) {
            return java.util.stream.IntStream.rangeClosed(1, x)
                    .filter(i -> x % i == 0)
                    .boxed()
                    .toList();
        }
    }

    @RequestScoped
    public static class ChatMemoryBean implements ChatMemoryProvider {

        private final Map<Object, ChatMemory> memories = new ConcurrentHashMap<>();

        @Override
        public ChatMemory get(Object memoryId) {
            return memories.computeIfAbsent(memoryId, id -> MessageWindowChatMemory.builder()
                    .maxMessages(20)
                    .id(memoryId)
                    .build());
        }

        @PreDestroy
        public void close() {
            memories.clear();
        }
    }
}
