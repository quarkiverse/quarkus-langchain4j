package io.quarkiverse.langchain4j.agentic.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.scope.AgenticScopeSerializer;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;
import testdomain.Order;

public class AgenticScopeDeserializationTest extends OpenAiBaseTest {

    public static class FixedChatModel implements ChatModel {
        @Override
        public ChatResponse doChat(ChatRequest request) {
            return ChatResponse.builder().aiMessage(new AiMessage("Order processed")).build();
        }
    }

    public interface OrderAgent {

        @Agent(description = "Process an order", outputKey = "result")
        ResultWithAgenticScope<String> processOrder(@V("order") Order order);

        @ChatModelSupplier
        static ChatModel chatModel() {
            return new FixedChatModel();
        }
    }

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(Order.class, OrderAgent.class, FixedChatModel.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "whatever")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    @Inject
    OrderAgent orderAgent;

    @Test
    void domainObjectInAgenticScopeSurvivesSerializationRoundTrip() {
        DefaultAgenticScope scope = DefaultAgenticScope.ephemeralAgenticScope();
        scope.writeState("order", new Order("ORD-1", "Widget", 3));

        String json = AgenticScopeSerializer.toJson(scope);
        DefaultAgenticScope restored = AgenticScopeSerializer.fromJson(json);

        Object restoredOrder = restored.readState("order");
        assertThat(restoredOrder).isInstanceOf(Order.class);
        Order order = (Order) restoredOrder;
        assertThat(order.id).isEqualTo("ORD-1");
        assertThat(order.product).isEqualTo("Widget");
        assertThat(order.quantity).isEqualTo(3);
    }
}
