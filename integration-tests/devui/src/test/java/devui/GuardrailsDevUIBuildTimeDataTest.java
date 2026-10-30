package devui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.JsonNode;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.guardrail.InputGuardrails;
import dev.langchain4j.service.guardrail.OutputGuardrails;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.guardrails.ToolInputGuardrail;
import io.quarkiverse.langchain4j.guardrails.ToolInputGuardrailRequest;
import io.quarkiverse.langchain4j.guardrails.ToolInputGuardrailResult;
import io.quarkiverse.langchain4j.guardrails.ToolInputGuardrails;
import io.quarkiverse.langchain4j.runtime.aiservice.NoopChatMemory;
import io.quarkus.devui.tests.DevUIBuildTimeDataTest;
import io.quarkus.test.QuarkusDevModeTest;

/**
 * Verifies the "guardrails" build-time data that backs the Dev UI Guardrails page: guardrails are grouped by class,
 * a guardrail shared by several AI services lists all of them, and output guardrails carry their max retries.
 */
public class GuardrailsDevUIBuildTimeDataTest extends DevUIBuildTimeDataTest {

    @RegisterExtension
    static final QuarkusDevModeTest config = new QuarkusDevModeTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(FirstService.class, SecondService.class,
                            PiiInputGuardrail.class, KeywordInputGuardrail.class, ProfanityOutputGuardrail.class,
                            MyTools.class, AuthToolGuardrail.class,
                            MyChatModelSupplier.class, MyChatModel.class, MyMemoryProviderSupplier.class));

    public GuardrailsDevUIBuildTimeDataTest() {
        super("io.quarkiverse.langchain4j.quarkus-langchain4j-core");
    }

    @Test
    public void guardrailsBuildTimeDataIsExposed() throws Exception {
        JsonNode guardrails = getBuildTimeData("guardrails");
        assertNotNull(guardrails);
        assertTrue(guardrails.isArray());

        JsonNode pii = findByClassNameAndKind(guardrails, PiiInputGuardrail.class.getName(), "Input");
        assertNotNull(pii, "shared input guardrail row");
        assertEquals(2, pii.get("usedBy").size());

        // Class-level guardrails are reported with no specific method, and list the methods that override them.
        JsonNode piiOnFirst = usageForOwner(pii, FirstService.class.getName());
        assertTrue(piiOnFirst.get("method").isNull(), "class-level guardrail should have no method");
        assertEquals("secure", piiOnFirst.get("excludedMethods").get(0).asText(),
                "the method declaring its own input guardrail overrides the class-level one");

        JsonNode piiOnSecond = usageForOwner(pii, SecondService.class.getName());
        assertTrue(piiOnSecond.get("method").isNull(), "class-level guardrail should have no method");
        assertTrue(piiOnSecond.get("excludedMethods").isEmpty(), "no method overrides it on SecondService");

        // The output guardrail is declared on a method, so it is reported against that method.
        JsonNode profanity = findByClassNameAndKind(guardrails, ProfanityOutputGuardrail.class.getName(), "Output");
        assertNotNull(profanity, "output guardrail row");
        JsonNode usage = profanity.get("usedBy").get(0);
        assertEquals(FirstService.class.getName(), usage.get("owner").asText());
        assertEquals("chat", usage.get("method").asText());
        assertEquals(1, usage.get("position").asInt());
        assertEquals(4, usage.get("maxRetries").asInt());

        JsonNode toolGuardrail = findByClassNameAndKind(guardrails, AuthToolGuardrail.class.getName(), "Tool input");
        assertNotNull(toolGuardrail, "tool input guardrail row");
        JsonNode toolUsage = toolGuardrail.get("usedBy").get(0);
        assertEquals(MyTools.class.getName(), toolUsage.get("owner").asText());
        assertEquals("sendEmail", toolUsage.get("method").asText());
        assertTrue(toolUsage.get("maxRetries").isNull(), "tool guardrails carry no max retries");
    }

    private static JsonNode findByClassNameAndKind(JsonNode guardrails, String className, String kind) {
        for (JsonNode node : guardrails) {
            if (className.equals(node.get("className").asText()) && kind.equals(node.get("kind").asText())) {
                return node;
            }
        }
        return null;
    }

    private static JsonNode usageForOwner(JsonNode guardrail, String owner) {
        for (JsonNode usage : guardrail.get("usedBy")) {
            if (owner.equals(usage.get("owner").asText())) {
                return usage;
            }
        }
        return null;
    }

    @RegisterAiService(chatLanguageModelSupplier = MyChatModelSupplier.class, chatMemoryProviderSupplier = MyMemoryProviderSupplier.class, tools = MyTools.class)
    @InputGuardrails(PiiInputGuardrail.class)
    public interface FirstService {
        // Output guardrail declared on the method (the common pattern), not on the class
        @OutputGuardrails(value = ProfanityOutputGuardrail.class, maxRetries = 4)
        @UserMessage("Say hi")
        String chat(@MemoryId String mem);

        // Declares its own input guardrail, so it overrides the class-level PiiInputGuardrail.
        @InputGuardrails(KeywordInputGuardrail.class)
        @UserMessage("Secure hi")
        String secure(@MemoryId String mem);
    }

    @ApplicationScoped
    public static class MyTools {
        @Tool("Send an email")
        @ToolInputGuardrails(AuthToolGuardrail.class)
        public String sendEmail(String to) {
            return "sent";
        }
    }

    @ApplicationScoped
    public static class AuthToolGuardrail implements ToolInputGuardrail {
        @Override
        public ToolInputGuardrailResult validate(ToolInputGuardrailRequest request) {
            return ToolInputGuardrailResult.success();
        }
    }

    @RegisterAiService(chatLanguageModelSupplier = MyChatModelSupplier.class, chatMemoryProviderSupplier = MyMemoryProviderSupplier.class)
    @InputGuardrails(PiiInputGuardrail.class)
    public interface SecondService {
        @UserMessage("Say hi")
        String hi(@MemoryId String mem);
    }

    @ApplicationScoped
    public static class PiiInputGuardrail implements InputGuardrail {
        @Override
        public InputGuardrailResult validate(dev.langchain4j.data.message.UserMessage ignored) {
            return success();
        }
    }

    @ApplicationScoped
    public static class KeywordInputGuardrail implements InputGuardrail {
        @Override
        public InputGuardrailResult validate(dev.langchain4j.data.message.UserMessage ignored) {
            return success();
        }
    }

    @ApplicationScoped
    public static class ProfanityOutputGuardrail implements OutputGuardrail {
        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            return success();
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
        public ChatResponse doChat(ChatRequest request) {
            return ChatResponse.builder().aiMessage(new AiMessage("Hi!")).build();
        }
    }

    public static class MyMemoryProviderSupplier implements Supplier<ChatMemoryProvider> {
        @Override
        public ChatMemoryProvider get() {
            return memoryId -> new NoopChatMemory();
        }
    }
}
