package io.quarkiverse.langchain4j.test.guardrails;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
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
import io.smallrye.mutiny.Uni;

/**
 * Tests for tools returning reactive types (Uni) with guardrails.
 * Covers:
 * - Tools returning Uni<String>
 * - Tools returning Uni<Object>
 * - Input/output guardrails with reactive tools
 * - Async execution with guardrails
 */
public class ToolGuardrailWithUniTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(
                            MyAiService.class,
                            MyTools.class,
                            Product.class,
                            InventoryStatus.class,
                            MessageValidationGuardrail.class,
                            ProductIdValidationGuardrail.class,
                            PriceFilterGuardrail.class,
                            InventoryStatusTransformGuardrail.class,
                            Lists.class));

    @Inject
    MyAiService aiService;

    @Inject
    MyTools tools;

    @BeforeEach
    void setUp() {
        tools.reset();
        MessageValidationGuardrail.reset();
        ProductIdValidationGuardrail.reset();
        PriceFilterGuardrail.reset();
        InventoryStatusTransformGuardrail.reset();
    }

    @Test
    @ActivateRequestContext
    void testToolReturningUniString() {
        String result = aiService.chat("test", "getMessage - hello");

        assertThat(tools.getMessageExecuted).isTrue();
        assertThat(result).contains("Message: hello");
        assertThat(result).contains("async");
    }

    @Test
    @ActivateRequestContext
    void testToolReturningUniObject() {
        String result = aiService.chat("test", "getProduct - 123");

        assertThat(tools.getProductExecuted).isTrue();
        assertThat(result).contains("123"); // Product ID
        assertThat(result).contains("name"); // JSON field
        assertThat(result).contains("Laptop");
    }

    @Test
    @ActivateRequestContext
    void testToolReturningUniObjectWithDelay() {
        String result = aiService.chat("test", "getProductDelayed - 456");

        assertThat(tools.getProductDelayedExecuted).isTrue();
        assertThat(result).contains("Tablet");
        assertThat(result).contains("price");
    }

    @Test
    @ActivateRequestContext
    void testToolReturningUniNestedObject() {
        String result = aiService.chat("test", "getInventoryStatus - 789");

        assertThat(tools.getInventoryStatusExecuted).isTrue();
        assertThat(result).contains("product");
        assertThat(result).contains("inStock");
        assertThat(result).contains("quantity");
    }

    @Test
    @ActivateRequestContext
    void testInputGuardrail_withReactiveTool_success() {
        String result = aiService.chat("test", "getValidatedMessage - validmessage");

        assertThat(MessageValidationGuardrail.executionCount).isEqualTo(1);
        assertThat(MessageValidationGuardrail.lastMessage).isEqualTo("validmessage");
        assertThat(tools.getValidatedMessageExecuted).isTrue();
        assertThat(result).contains("Validated: validmessage");
    }

    @Test
    @ActivateRequestContext
    void testInputGuardrail_withReactiveTool_failure() {
        String result = aiService.chat("test", "getValidatedMessage - bad!");

        assertThat(MessageValidationGuardrail.executionCount).isEqualTo(1);
        assertThat(MessageValidationGuardrail.lastMessage).isEqualTo("bad!");
        assertThat(tools.getValidatedMessageExecuted).isFalse();
        assertThat(result).contains("Invalid message");
    }

    @Test
    @ActivateRequestContext
    void testInputGuardrail_withReactiveObjectTool_success() {
        String result = aiService.chat("test", "getValidatedProduct - 100");

        assertThat(ProductIdValidationGuardrail.executionCount).isEqualTo(1);
        assertThat(tools.getValidatedProductExecuted).isTrue();
        assertThat(result).contains("Validated Product");
    }

    @Test
    @ActivateRequestContext
    void testInputGuardrail_withReactiveObjectTool_failure() {
        String result = aiService.chat("test", "getValidatedProduct - 999");

        assertThat(ProductIdValidationGuardrail.executionCount).isEqualTo(1);
        assertThat(tools.getValidatedProductExecuted).isFalse();
        assertThat(result).contains("Product ID must be less than 500");
    }

    @Test
    @ActivateRequestContext
    void testOutputGuardrail_withReactiveStringTool() {
        String result = aiService.chat("test", "getFilteredMessage - price is $99.99");

        assertThat(tools.getFilteredMessageExecuted).isTrue();
        assertThat(PriceFilterGuardrail.executionCount).isEqualTo(1);

        // Price should be filtered
        assertThat(result).doesNotContain("$99.99");
        assertThat(result).contains("[PRICE REDACTED]");
    }

    @Test
    @ActivateRequestContext
    void testOutputGuardrail_withReactiveObjectTool() {
        String result = aiService.chat("test", "getFilteredProduct - 200");

        assertThat(tools.getFilteredProductExecuted).isTrue();
        assertThat(PriceFilterGuardrail.executionCount).isEqualTo(1);

        // Price field should be redacted in JSON
        assertThat(result).contains("price");
        assertThat(result).contains("REDACTED");
        assertThat(result).doesNotContain("999.99");
    }

    @Test
    @ActivateRequestContext
    void testOutputGuardrail_transformReactiveObject() {
        String result = aiService.chat("test", "getTransformedInventory - 300");

        assertThat(tools.getTransformedInventoryExecuted).isTrue();
        assertThat(InventoryStatusTransformGuardrail.executionCount).isEqualTo(1);

        // Quantity should be transformed to category
        assertThat(result).contains("availabilityCategory");
        assertThat(result).contains("HIGH_STOCK");
    }

    /**
     * Product POJO
     */
    public static class Product {
        private String id;
        private String name;
        private double price;

        public Product() {
        }

        public Product(String id, String name, double price) {
            this.id = id;
            this.name = name;
            this.price = price;
        }

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

        public double getPrice() {
            return price;
        }

        public void setPrice(double price) {
            this.price = price;
        }
    }

    /**
     * Inventory status with nested product
     */
    public static class InventoryStatus {
        private Product product;
        private boolean inStock;
        private int quantity;
        private String availabilityCategory;

        public InventoryStatus() {
        }

        public InventoryStatus(Product product, boolean inStock, int quantity) {
            this.product = product;
            this.inStock = inStock;
            this.quantity = quantity;
        }

        public Product getProduct() {
            return product;
        }

        public void setProduct(Product product) {
            this.product = product;
        }

        public boolean isInStock() {
            return inStock;
        }

        public void setInStock(boolean inStock) {
            this.inStock = inStock;
        }

        public int getQuantity() {
            return quantity;
        }

        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }

        public String getAvailabilityCategory() {
            return availabilityCategory;
        }

        public void setAvailabilityCategory(String category) {
            this.availabilityCategory = category;
        }
    }

    @RegisterAiService(chatLanguageModelSupplier = MyChatModelSupplier.class, chatMemoryProviderSupplier = MyMemoryProviderSupplier.class)
    public interface MyAiService {
        @ToolBox(MyTools.class)
        String chat(@MemoryId String memoryId, @UserMessage String userMessage);
    }

    @Singleton
    public static class MyTools {
        boolean getMessageExecuted = false;
        boolean getProductExecuted = false;
        boolean getProductDelayedExecuted = false;
        boolean getInventoryStatusExecuted = false;
        boolean getValidatedMessageExecuted = false;
        boolean getValidatedProductExecuted = false;
        boolean getFilteredMessageExecuted = false;
        boolean getFilteredProductExecuted = false;
        boolean getTransformedInventoryExecuted = false;

        @Tool
        public Uni<String> getMessage(String message) {
            return Uni.createFrom().item(() -> {
                getMessageExecuted = true;
                return "Message: " + message + " (processed on thread: " + Thread.currentThread().getName() + " - async)";
            });
        }

        @Tool
        public Uni<Product> getProduct(String productId) {
            return Uni.createFrom().item(() -> {
                getProductExecuted = true;
                return new Product(productId, "Laptop", 1299.99);
            });
        }

        @Tool
        public Uni<Product> getProductDelayed(String productId) {
            return Uni.createFrom().item(() -> {
                getProductDelayedExecuted = true;
                return new Product(productId, "Tablet", 599.99);
            }).onItem().delayIt().by(Duration.ofMillis(100));
        }

        @Tool
        public Uni<InventoryStatus> getInventoryStatus(String productId) {
            return Uni.createFrom().item(() -> {
                getInventoryStatusExecuted = true;
                Product product = new Product(productId, "Smartphone", 899.99);
                return new InventoryStatus(product, true, 150);
            });
        }

        @Tool
        @ToolInputGuardrails({ MessageValidationGuardrail.class })
        public Uni<String> getValidatedMessage(String message) {
            return Uni.createFrom().item(() -> {
                getValidatedMessageExecuted = true;
                return "Validated: " + message;
            });
        }

        @Tool
        @ToolInputGuardrails({ ProductIdValidationGuardrail.class })
        public Uni<Product> getValidatedProduct(String productId) {
            return Uni.createFrom().item(() -> {
                getValidatedProductExecuted = true;
                return new Product(productId, "Validated Product", 299.99);
            });
        }

        @Tool
        @ToolOutputGuardrails({ PriceFilterGuardrail.class })
        public Uni<String> getFilteredMessage(String message) {
            return Uni.createFrom().item(() -> {
                getFilteredMessageExecuted = true;
                return message;
            });
        }

        @Tool
        @ToolOutputGuardrails({ PriceFilterGuardrail.class })
        public Uni<Product> getFilteredProduct(String productId) {
            return Uni.createFrom().item(() -> {
                getFilteredProductExecuted = true;
                return new Product(productId, "Expensive Item", 999.99);
            });
        }

        @Tool
        @ToolOutputGuardrails({ InventoryStatusTransformGuardrail.class })
        public Uni<InventoryStatus> getTransformedInventory(String productId) {
            return Uni.createFrom().item(() -> {
                getTransformedInventoryExecuted = true;
                Product product = new Product(productId, "High Stock Item", 199.99);
                return new InventoryStatus(product, true, 500);
            });
        }

        void reset() {
            getMessageExecuted = false;
            getProductExecuted = false;
            getProductDelayedExecuted = false;
            getInventoryStatusExecuted = false;
            getValidatedMessageExecuted = false;
            getValidatedProductExecuted = false;
            getFilteredMessageExecuted = false;
            getFilteredProductExecuted = false;
            getTransformedInventoryExecuted = false;
        }
    }

    @ApplicationScoped
    public static class MessageValidationGuardrail implements ToolInputGuardrail {
        static int executionCount = 0;
        static String lastMessage = null;

        @Override
        public ToolInputGuardrailResult validate(ToolInputGuardrailRequest request) {
            executionCount++;
            try {
                ObjectMapper mapper = new ObjectMapper();
                var args = mapper.readTree(request.arguments());
                String message = args.get("message").asText();
                lastMessage = message;

                // Reject messages with special characters
                if (message.matches(".*[!@#$%^&*].*")) {
                    return ToolInputGuardrailResult.failure(
                            "Invalid message: contains special characters");
                }

                return ToolInputGuardrailResult.success();
            } catch (Exception e) {
                return ToolInputGuardrailResult.failure("Failed to validate message: " + e.getMessage(), e);
            }
        }

        static void reset() {
            executionCount = 0;
            lastMessage = null;
        }
    }

    @ApplicationScoped
    public static class ProductIdValidationGuardrail implements ToolInputGuardrail {
        static int executionCount = 0;

        @Override
        public ToolInputGuardrailResult validate(ToolInputGuardrailRequest request) {
            executionCount++;
            try {
                ObjectMapper mapper = new ObjectMapper();
                var args = mapper.readTree(request.arguments());
                String productId = args.get("productId").asText();

                // Validate product ID range
                int id = Integer.parseInt(productId);
                if (id >= 500) {
                    return ToolInputGuardrailResult.failure(
                            "Product ID must be less than 500");
                }

                return ToolInputGuardrailResult.success();
            } catch (Exception e) {
                return ToolInputGuardrailResult.failure("Failed to validate product ID: " + e.getMessage(), e);
            }
        }

        static void reset() {
            executionCount = 0;
        }
    }

    @ApplicationScoped
    public static class PriceFilterGuardrail implements ToolOutputGuardrail {
        static int executionCount = 0;

        @Override
        public ToolOutputGuardrailResult validate(ToolOutputGuardrailRequest request) {
            executionCount++;
            try {
                String resultText = request.resultText();

                // Filter dollar amounts from text
                String filtered = resultText.replaceAll("\\$[0-9]+\\.[0-9]{2}", "[PRICE REDACTED]");

                // Filter price field from JSON
                filtered = filtered.replaceAll("\"price\"\\s*:\\s*[0-9]+\\.[0-9]+", "\"price\":\"REDACTED\"");

                if (!filtered.equals(resultText)) {
                    return ToolOutputGuardrailResult.successWith(
                            ToolExecutionResult.builder()
                                    .resultText(filtered)
                                    .build());
                }

                return ToolOutputGuardrailResult.success();
            } catch (Exception e) {
                return ToolOutputGuardrailResult.failure("Failed to filter prices: " + e.getMessage(), e);
            }
        }

        static void reset() {
            executionCount = 0;
        }
    }

    @ApplicationScoped
    public static class InventoryStatusTransformGuardrail implements ToolOutputGuardrail {
        static int executionCount = 0;

        @Override
        public ToolOutputGuardrailResult validate(ToolOutputGuardrailRequest request) {
            executionCount++;
            try {
                String resultText = request.resultText();
                ObjectMapper mapper = new ObjectMapper();
                InventoryStatus status = mapper.readValue(resultText, InventoryStatus.class);

                // Transform quantity to availability category
                String category;
                if (status.getQuantity() > 100) {
                    category = "HIGH_STOCK";
                } else if (status.getQuantity() > 10) {
                    category = "MEDIUM_STOCK";
                } else {
                    category = "LOW_STOCK";
                }

                status.setAvailabilityCategory(category);

                String modifiedJson = mapper.writeValueAsString(status);

                return ToolOutputGuardrailResult.successWith(
                        ToolExecutionResult.builder()
                                .resultText(modifiedJson)
                                .build());
            } catch (Exception e) {
                return ToolOutputGuardrailResult.failure(
                        "Failed to transform inventory status: " + e.getMessage(), e);
            }
        }

        static void reset() {
            executionCount = 0;
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
                                .arguments("{\"message\":\"" + input + "\",\"productId\":\"" + input + "\"}")
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
