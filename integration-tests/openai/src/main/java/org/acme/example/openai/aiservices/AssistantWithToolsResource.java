package org.acme.example.openai.aiservices;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Singleton;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import org.jboss.resteasy.reactive.RestQuery;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import io.quarkiverse.langchain4j.RegisterAiService;

@Path("assistant-with-tool")
public class AssistantWithToolsResource {

    private final Assistant assistant;

    public AssistantWithToolsResource(Assistant assistant) {
        this.assistant = assistant;
    }

    public static class TestData {
        String foo;
        Integer bar;
        Double baz;

        TestData(String foo, Integer bar, Double baz) {
            this.foo = foo;
            this.bar = bar;
            this.baz = baz;
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

        @Tool("""
                Modifies an existing booking.
                This includes making changes to the flight date, and the departure and arrival airports.
                """)
        public void changeBooking(
                String bookingNumber,
                String firstName,
                String lastName,
                LocalDate newFlightDate,
                @P("3-letter code for departure airport") String newDepartureAirport,
                @P(value = "3-letter code for arrival airport", required = false) String newArrivalAirport) {

        }

        @Tool("Calculates the length of a string")
        int stringLength(@P(value = "The string to compute the length of", required = false) String s) {
            return (s == null) ? 0 : s.length();
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
        List<Integer> getFactors(@P("The integer to get factor") int x) {
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
