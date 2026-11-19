package io.quarkiverse.langchain4j.agentic.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Qualifier;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.declarative.ToolSearchStrategySupplier;
import dev.langchain4j.agentic.declarative.ToolsSupplier;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.tool.search.ToolSearchStrategy;
import dev.langchain4j.service.tool.search.simple.SimpleToolSearchStrategy;
import io.quarkiverse.langchain4j.agentic.runtime.CdiBean;
import io.quarkus.arc.Unremovable;
import io.quarkus.test.QuarkusExtensionTest;

public class AgentToolSearchStrategySupplierTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(new Supplier<JavaArchive>() {
                @Override
                public JavaArchive get() {
                    return ShrinkWrap.create(JavaArchive.class)
                            .addClasses(BaseAgent.class, SearchAgent.class, PlainAgent.class,
                                    RecordingModel.class, BookingTools.class, CdiSearchAgent.class, QualifiedSearchAgent.class,
                                    CdiSearchSettings.class, QualifiedSearchSettings.class, SearchBackend.class);
                }
            })
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "unused");

    @Inject
    SearchAgent searchAgent;

    @Inject
    PlainAgent plainAgent;

    @Inject
    RecordingModel model;

    @Inject
    Instance<CdiSearchAgent> cdiSearchAgent;

    @Inject
    @Any
    Instance<QualifiedSearchAgent> qualifiedSearchAgent;

    @Test
    void supplierReplacesTheInitialCatalogWithItsSearchTool() {
        assertThat(plainAgent.chat("Find my booking")).isEqualTo("done");
        assertThat(model.toolNames()).containsExactly("getBookingDetails");

        assertThat(searchAgent.chat("Find my booking")).isEqualTo("done");
        assertThat(model.toolNames()).containsExactly("find_tools");
    }

    @Test
    void supplierKeepsItsCdiParameterBeanAvailable() {
        assertThat(cdiSearchAgent.get().chat("Find my booking")).isEqualTo("done");
        assertThat(model.toolNames()).containsExactly("cdi_tools");
    }

    @Test
    void supplierResolvesAQualifierWithoutCdiBean() {
        assertThat(qualifiedSearchAgent.get().chat("Find my booking")).isEqualTo("done");
        assertThat(model.toolNames()).containsExactly("qualified_tools");
    }

    public interface BaseAgent {
        @ChatModelSupplier
        static ChatModel model(@CdiBean RecordingModel model) {
            return model;
        }

        @ToolsSupplier
        static Object[] tools() {
            return new Object[] { new BookingTools() };
        }
    }

    public interface SearchAgent extends BaseAgent {
        @Agent
        String chat(@UserMessage String message);

        @ToolSearchStrategySupplier
        static ToolSearchStrategy searchStrategy() {
            return SimpleToolSearchStrategy.builder().toolName("find_tools").build();
        }
    }

    public interface PlainAgent extends BaseAgent {
        @Agent
        String chat(@UserMessage String message);
    }

    public interface CdiSearchAgent extends BaseAgent {
        @Agent
        String chat(@UserMessage String message);

        @ToolSearchStrategySupplier
        static ToolSearchStrategy searchStrategy(@CdiBean CdiSearchSettings settings) {
            return settings.strategy();
        }
    }

    public interface QualifiedSearchAgent extends BaseAgent {
        @Agent
        String chat(@UserMessage String message);

        @ToolSearchStrategySupplier
        static ToolSearchStrategy searchStrategy(@SearchBackend QualifiedSearchSettings settings) {
            return settings.strategy();
        }
    }

    @Singleton
    public static class CdiSearchSettings {
        public ToolSearchStrategy strategy() {
            return SimpleToolSearchStrategy.builder().toolName("cdi_tools").build();
        }
    }

    @Singleton
    // Keep this bean so that the test isolates qualifier registration from unused-bean removal.
    @Unremovable
    @SearchBackend
    public static class QualifiedSearchSettings {
        public ToolSearchStrategy strategy() {
            return SimpleToolSearchStrategy.builder().toolName("qualified_tools").build();
        }
    }

    @Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ ElementType.TYPE, ElementType.PARAMETER })
    public @interface SearchBackend {
    }

    public static class BookingTools {
        @Tool("Get booking details")
        public String getBookingDetails() {
            return "booking";
        }
    }

    @Singleton
    public static class RecordingModel implements ChatModel {
        private List<String> toolNames = List.of();

        @Override
        public ChatResponse doChat(ChatRequest request) {
            toolNames = new ArrayList<>();
            for (ToolSpecification tool : request.toolSpecifications()) {
                toolNames.add(tool.name());
            }
            return ChatResponse.builder().aiMessage(AiMessage.from("done")).build();
        }

        public List<String> toolNames() {
            return toolNames;
        }
    }
}
