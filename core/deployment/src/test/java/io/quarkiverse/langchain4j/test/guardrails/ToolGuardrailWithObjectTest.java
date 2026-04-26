package io.quarkiverse.langchain4j.test.guardrails;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.tool.ToolExecutionResult;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.ToolBox;
import io.quarkiverse.langchain4j.guardrails.ToolInputGuardrail;
import io.quarkiverse.langchain4j.guardrails.ToolInputGuardrailRequest;
import io.quarkiverse.langchain4j.guardrails.ToolInputGuardrailResult;
import io.quarkiverse.langchain4j.guardrails.ToolInputGuardrails;
import io.quarkiverse.langchain4j.guardrails.ToolOutputGuardrail;
import io.quarkiverse.langchain4j.guardrails.ToolOutputGuardrailRequest;
import io.quarkiverse.langchain4j.guardrails.ToolOutputGuardrailResult;
import io.quarkiverse.langchain4j.guardrails.ToolOutputGuardrails;
import io.quarkiverse.langchain4j.runtime.aiservice.NoopChatMemory;
import io.quarkiverse.langchain4j.test.Lists;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Tests for tools that return structured objects instead of strings.
 * Covers:
 * - Tools returning POJOs/records
 * - Tools returning lists of objects
 * - Tools returning nested objects
 * - Input/output guardrails working with structured data
 */
public class ToolGuardrailWithObjectTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(
                            MyAiService.class,
                            MyTools.class,
                            Customer.class,
                            Address.class,
                            CustomerListResponse.class,
                            IdValidationGuardrail.class,
                            SensitiveFieldFilter.class,
                            CustomerListSizeGuardrail.class,
                            Lists.class));

    @Inject
    MyAiService aiService;

    @Inject
    MyTools tools;

    @BeforeEach
    void setUp() {
        tools.reset();
        IdValidationGuardrail.reset();
        SensitiveFieldFilter.reset();
        CustomerListSizeGuardrail.reset();
    }

    @Test
    @ActivateRequestContext
    void testToolReturningSimpleObject() {
        String result = aiService.chat("test", "getCustomer - 123");

        assertThat(tools.getCustomerExecuted).isTrue();
        assertThat(result).contains("123"); // ID
        assertThat(result).contains("John Doe");
        assertThat(result).contains("john@example.com");
    }

    @Test
    @ActivateRequestContext
    void testToolReturningRecord() {
        String result = aiService.chat("test", "getAddress - 456");

        assertThat(tools.getAddressExecuted).isTrue();
        assertThat(result).contains("street"); // JSON field
        assertThat(result).contains("123 Main St");
        assertThat(result).contains("New York");
    }

    @Test
    @ActivateRequestContext
    void testToolReturningListOfObjects() {
        String result = aiService.chat("test", "listCustomers - premium");

        assertThat(tools.listCustomersExecuted).isTrue();
        assertThat(result).contains("customers");
        assertThat(result).contains("Alice");
        assertThat(result).contains("Bob");
    }

    @Test
    @ActivateRequestContext
    void testToolReturningNestedObject() {
        String result = aiService.chat("test", "getCustomerWithAddress - 789");

        assertThat(tools.getCustomerWithAddressExecuted).isTrue();
        assertThat(result).contains("Charlie"); // Customer name
        assertThat(result).contains("address"); // Nested object field
        assertThat(result).contains("456 Oak Ave");
    }

    @Test
    @ActivateRequestContext
    void testInputGuardrail_withStructuredObjectTool_success() {
        String result = aiService.chat("test", "getValidatedCustomer - 100");

        assertThat(IdValidationGuardrail.executionCount).isEqualTo(1);
        assertThat(IdValidationGuardrail.lastValidatedId).isEqualTo("100");
        assertThat(tools.getValidatedCustomerExecuted).isTrue();
        assertThat(result).contains("Valid Customer"); // Customer name
    }

    @Test
    @ActivateRequestContext
    void testInputGuardrail_withStructuredObjectTool_failure() {
        String result = aiService.chat("test", "getValidatedCustomer - -5");

        assertThat(IdValidationGuardrail.executionCount).isEqualTo(1);
        assertThat(IdValidationGuardrail.lastValidatedId).isEqualTo("-5");
        assertThat(tools.getValidatedCustomerExecuted).isFalse();
        assertThat(result).contains("Invalid customer ID");
    }

    @Test
    @ActivateRequestContext
    void testOutputGuardrail_filterSensitiveFieldsFromObject() {
        String result = aiService.chat("test", "getFilteredCustomer - 200");

        assertThat(tools.getFilteredCustomerExecuted).isTrue();
        assertThat(SensitiveFieldFilter.executionCount).isEqualTo(1);

        // SSN should be filtered
        assertThat(result).doesNotContain("123-45-6789");
        assertThat(result).contains("[REDACTED]");

        // Other fields should remain
        assertThat(result).contains("Jane Smith");
        assertThat(result).contains("jane@example.com");
    }

    @Test
    @ActivateRequestContext
    void testOutputGuardrail_transformStructuredObject() {
        String result = aiService.chat("test", "getLimitedCustomers - all");

        assertThat(tools.getLimitedCustomersExecuted).isTrue();
        assertThat(CustomerListSizeGuardrail.executionCount).isEqualTo(1);

        // Original list has 5 customers, guardrail limits to 3
        assertThat(CustomerListSizeGuardrail.originalSize).isEqualTo(5);
        assertThat(CustomerListSizeGuardrail.limitedSize).isEqualTo(3);

        // Should contain truncation notice
        assertThat(result).contains("truncated");
        assertThat(result).contains("showing 3 of 5");
    }

    @Test
    @ActivateRequestContext
    void testOutputGuardrail_withNestedObject_filtersSensitiveData() {
        String result = aiService.chat("test", "getFilteredCustomerWithAddress - 300");

        assertThat(tools.getFilteredCustomerWithAddressExecuted).isTrue();
        assertThat(SensitiveFieldFilter.executionCount).isEqualTo(1);

        // SSN should be filtered even in nested structure
        assertThat(result).doesNotContain("987-65-4321");
        assertThat(result).contains("[REDACTED]");

        // Other nested data should remain
        assertThat(result).contains("789 Elm St");
        assertThat(result).contains("Los Angeles");
    }

    /**
     * Simple POJO customer class
     */
    public static class Customer {
        private String id;
        private String name;
        private String email;
        private String ssn;
        private Address address;

        public Customer() {
        }

        public Customer(String id, String name, String email, String ssn) {
            this.id = id;
            this.name = name;
            this.email = email;
            this.ssn = ssn;
        }

        public Customer(String id, String name, String email, String ssn, Address address) {
            this.id = id;
            this.name = name;
            this.email = email;
            this.ssn = ssn;
            this.address = address;
        }

        // Getters and setters
        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getSsn() {
            return ssn;
        }

        public void setSsn(String ssn) {
            this.ssn = ssn;
        }

        public Address getAddress() {
            return address;
        }

        public void setAddress(Address address) {
            this.address = address;
        }
    }

    /**
     * Record for address (Java 17+)
     */
    public record Address(String street, String city, String zipCode) {
    }

    /**
     * Response wrapper for list of customers
     */
    public static class CustomerListResponse {
        private List<Customer> customers;
        private int total;
        private boolean truncated;
        private String message;

        public CustomerListResponse() {
        }

        public CustomerListResponse(List<Customer> customers, int total, boolean truncated, String message) {
            this.customers = customers;
            this.total = total;
            this.truncated = truncated;
            this.message = message;
        }

        // Getters and setters
        public List<Customer> getCustomers() {
            return customers;
        }

        public void setCustomers(List<Customer> customers) {
            this.customers = customers;
        }

        public int getTotal() {
            return total;
        }

        public void setTotal(int total) {
            this.total = total;
        }

        public boolean isTruncated() {
            return truncated;
        }

        public void setTruncated(boolean truncated) {
            this.truncated = truncated;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }

    @RegisterAiService(chatLanguageModelSupplier = MyChatModelSupplier.class, chatMemoryProviderSupplier = MyMemoryProviderSupplier.class)
    public interface MyAiService {
        @ToolBox(MyTools.class)
        String chat(@MemoryId String memoryId, @UserMessage String userMessage);
    }

    @Singleton
    public static class MyTools {
        boolean getCustomerExecuted = false;
        boolean getAddressExecuted = false;
        boolean listCustomersExecuted = false;
        boolean getCustomerWithAddressExecuted = false;
        boolean getValidatedCustomerExecuted = false;
        boolean getFilteredCustomerExecuted = false;
        boolean getLimitedCustomersExecuted = false;
        boolean getFilteredCustomerWithAddressExecuted = false;

        @Tool
        public Customer getCustomer(String customerId) {
            getCustomerExecuted = true;
            return new Customer(customerId, "John Doe", "john@example.com", "111-22-3333");
        }

        @Tool
        public Address getAddress(String addressId) {
            getAddressExecuted = true;
            return new Address("123 Main St", "New York", "10001");
        }

        @Tool
        public CustomerListResponse listCustomers(String filter) {
            listCustomersExecuted = true;
            List<Customer> customers = List.of(
                    new Customer("1", "Alice", "alice@example.com", "111-11-1111"),
                    new Customer("2", "Bob", "bob@example.com", "222-22-2222"));
            return new CustomerListResponse(customers, customers.size(), false, null);
        }

        @Tool
        public Customer getCustomerWithAddress(String customerId) {
            getCustomerWithAddressExecuted = true;
            Address address = new Address("456 Oak Ave", "Boston", "02101");
            return new Customer(customerId, "Charlie", "charlie@example.com", "333-33-3333", address);
        }

        @Tool
        @ToolInputGuardrails({ IdValidationGuardrail.class })
        public Customer getValidatedCustomer(String customerId) {
            getValidatedCustomerExecuted = true;
            return new Customer(customerId, "Valid Customer", "valid@example.com", "444-44-4444");
        }

        @Tool
        @ToolOutputGuardrails({ SensitiveFieldFilter.class })
        public Customer getFilteredCustomer(String customerId) {
            getFilteredCustomerExecuted = true;
            return new Customer(customerId, "Jane Smith", "jane@example.com", "123-45-6789");
        }

        @Tool
        @ToolOutputGuardrails({ CustomerListSizeGuardrail.class })
        public CustomerListResponse getLimitedCustomers(String filter) {
            getLimitedCustomersExecuted = true;
            List<Customer> customers = List.of(
                    new Customer("1", "Customer 1", "c1@example.com", "111-11-1111"),
                    new Customer("2", "Customer 2", "c2@example.com", "222-22-2222"),
                    new Customer("3", "Customer 3", "c3@example.com", "333-33-3333"),
                    new Customer("4", "Customer 4", "c4@example.com", "444-44-4444"),
                    new Customer("5", "Customer 5", "c5@example.com", "555-55-5555"));
            return new CustomerListResponse(customers, customers.size(), false, null);
        }

        @Tool
        @ToolOutputGuardrails({ SensitiveFieldFilter.class })
        public Customer getFilteredCustomerWithAddress(String customerId) {
            getFilteredCustomerWithAddressExecuted = true;
            Address address = new Address("789 Elm St", "Los Angeles", "90001");
            return new Customer(customerId, "Nested Customer", "nested@example.com", "987-65-4321", address);
        }

        void reset() {
            getCustomerExecuted = false;
            getAddressExecuted = false;
            listCustomersExecuted = false;
            getCustomerWithAddressExecuted = false;
            getValidatedCustomerExecuted = false;
            getFilteredCustomerExecuted = false;
            getLimitedCustomersExecuted = false;
            getFilteredCustomerWithAddressExecuted = false;
        }
    }

    @ApplicationScoped
    public static class IdValidationGuardrail implements ToolInputGuardrail {
        static int executionCount = 0;
        static String lastValidatedId = null;

        @Override
        public ToolInputGuardrailResult validate(ToolInputGuardrailRequest request) {
            executionCount++;
            try {
                ObjectMapper mapper = new ObjectMapper();
                var args = mapper.readTree(request.arguments());
                String customerId = args.get("customerId").asText();
                lastValidatedId = customerId;

                // Validate ID is positive number
                int id = Integer.parseInt(customerId);
                if (id <= 0) {
                    return ToolInputGuardrailResult.failure(
                            "Invalid customer ID: " + customerId + ". ID must be a positive number.");
                }

                return ToolInputGuardrailResult.success();
            } catch (Exception e) {
                return ToolInputGuardrailResult.fatal("Failed to validate customer ID: " + e.getMessage(), e);
            }
        }

        static void reset() {
            executionCount = 0;
            lastValidatedId = null;
        }
    }

    @ApplicationScoped
    public static class SensitiveFieldFilter implements ToolOutputGuardrail {
        static int executionCount = 0;

        @Override
        public ToolOutputGuardrailResult validate(ToolOutputGuardrailRequest request) {
            executionCount++;
            try {
                String resultText = request.resultText();

                // Parse the JSON result
                ObjectMapper mapper = new ObjectMapper();
                var jsonNode = mapper.readTree(resultText);

                // Filter SSN fields (handles both direct and nested)
                String filtered = resultText.replaceAll("\"ssn\"\\s*:\\s*\"[^\"]+\"", "\"ssn\":\"[REDACTED]\"");

                if (!filtered.equals(resultText)) {
                    return ToolOutputGuardrailResult.successWith(
                            ToolExecutionResult.builder()
                                    .resultText(filtered)
                                    .build());
                }

                return ToolOutputGuardrailResult.success();
            } catch (Exception e) {
                return ToolOutputGuardrailResult.fatal("Failed to filter sensitive fields: " + e.getMessage(), e);
            }
        }

        static void reset() {
            executionCount = 0;
        }
    }

    @ApplicationScoped
    public static class CustomerListSizeGuardrail implements ToolOutputGuardrail {
        static int executionCount = 0;
        static int originalSize = 0;
        static int limitedSize = 0;
        private static final int MAX_CUSTOMERS = 3;

        @Override
        public ToolOutputGuardrailResult validate(ToolOutputGuardrailRequest request) {
            executionCount++;
            try {
                String resultText = request.resultText();
                ObjectMapper mapper = new ObjectMapper();
                CustomerListResponse response = mapper.readValue(resultText, CustomerListResponse.class);

                originalSize = response.getCustomers().size();

                if (originalSize > MAX_CUSTOMERS) {
                    // Limit the list
                    List<Customer> limited = response.getCustomers().subList(0, MAX_CUSTOMERS);
                    limitedSize = limited.size();

                    CustomerListResponse modifiedResponse = new CustomerListResponse(
                            limited,
                            originalSize,
                            true,
                            "Results truncated - showing " + limitedSize + " of " + originalSize + " customers");

                    String modifiedJson = mapper.writeValueAsString(modifiedResponse);

                    return ToolOutputGuardrailResult.successWith(
                            ToolExecutionResult.builder()
                                    .resultText(modifiedJson)
                                    .build());
                }

                limitedSize = originalSize;
                return ToolOutputGuardrailResult.success();
            } catch (Exception e) {
                return ToolOutputGuardrailResult.fatal("Failed to limit customer list: " + e.getMessage(), e);
            }
        }

        static void reset() {
            executionCount = 0;
            originalSize = 0;
            limitedSize = 0;
        }
    }

    public static class MyChatModelSupplier implements Supplier<ChatModel> {
        @Override
        public ChatModel get() {
            return new MyChatModel();
        }
    }

    public static class MyChatModel implements ChatModel {
        @Override
        public ChatResponse chat(List<ChatMessage> messages) {
            throw new UnsupportedOperationException("Should not be called");
        }

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            List<ChatMessage> messages = chatRequest.messages();
            if (messages.size() == 1) {
                String text = ((dev.langchain4j.data.message.UserMessage) messages.get(0)).singleText();
                String[] segments = text.split(" - ");
                String toolName = segments[0];
                String input = segments.length > 1 ? segments[1] : "";

                return ChatResponse.builder()
                        .aiMessage(new AiMessage("executing tool", List.of(ToolExecutionRequest.builder()
                                .id("tool-id-1")
                                .name(toolName)
                                .arguments("{\"customerId\":\"" + input + "\",\"addressId\":\"" + input
                                        + "\",\"filter\":\"" + input + "\"}")
                                .build())))
                        .tokenUsage(new TokenUsage(0, 0))
                        .finishReason(FinishReason.TOOL_EXECUTION)
                        .build();
            } else if (messages.size() == 3) {
                ToolExecutionResultMessage last = (ToolExecutionResultMessage) Lists.last(messages);
                return ChatResponse.builder()
                        .aiMessage(AiMessage.from("response: " + last.text()))
                        .build();
            }
            return ChatResponse.builder()
                    .aiMessage(new AiMessage("Unexpected"))
                    .build();
        }
    }

    public static class MyMemoryProviderSupplier implements Supplier<ChatMemoryProvider> {
        @Override
        public ChatMemoryProvider get() {
            return memoryId -> new NoopChatMemory();
        }
    }
}
