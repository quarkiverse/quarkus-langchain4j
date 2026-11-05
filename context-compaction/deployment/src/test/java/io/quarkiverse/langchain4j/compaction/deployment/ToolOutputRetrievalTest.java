package io.quarkiverse.langchain4j.compaction.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.compaction.ArchivedToolOutputs;
import io.quarkiverse.langchain4j.compaction.CompactionMessages;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * With {@code tool-output-pruning.retrieval=tool}, the stub of a pruned output gives a reference, and the model reads
 * the full output again by calling {@link ArchivedToolOutputs}. The provider reuses the same tool-call id for every
 * call, so the references must still be distinct.
 */
class ToolOutputRetrievalTest {

    static final int OUTPUT_CHARS = 3000;

    @RegisterExtension
    static final QuarkusExtensionTest test = new QuarkusExtensionTest()
            .withApplicationRoot(jar -> jar.addClasses(Agent.class, Orders.class, FakeModel.class, FakeModelSupplier.class))
            .overrideConfigKey("quarkus.langchain4j.compaction.strategy", "tool-output-pruning")
            .overrideConfigKey("quarkus.langchain4j.compaction.tool-output-pruning.keep-last-turns", "1")
            .overrideConfigKey("quarkus.langchain4j.compaction.tool-output-pruning.retrieval", "tool")
            .overrideConfigKey("quarkus.langchain4j.compaction.budget.context-window", "2000")
            .overrideConfigKey("quarkus.langchain4j.compaction.budget.reserved-completion-tokens", "0");

    @RegisterAiService(chatLanguageModelSupplier = FakeModelSupplier.class, tools = { Orders.class,
            ArchivedToolOutputs.class })
    interface Agent {
        @SystemMessage("You answer questions about orders.")
        String chat(@MemoryId String id, @UserMessage String question);
    }

    @ApplicationScoped
    public static class Orders {
        @Tool("Looks up an order")
        public String lookupOrder(String id) {
            return output(id);
        }

        static String output(String id) {
            String line = "order " + id + " line; ";
            return line.repeat(OUTPUT_CHARS / line.length());
        }
    }

    /**
     * Calls lookupOrder for "Look up order N" (always with the id {@code call_0}), and retrieveToolOutput with the
     * reference of the first pruned output for "Read the first order again".
     */
    @Singleton
    public static class FakeModel implements ChatModel {
        final List<String> retrieved = new CopyOnWriteArrayList<>();
        final List<List<ChatMessage>> requests = new CopyOnWriteArrayList<>();

        @Override
        public ChatResponse doChat(ChatRequest request) {
            requests.add(request.messages());
            ChatMessage last = request.messages().get(request.messages().size() - 1);
            if (last instanceof ToolExecutionResultMessage result) {
                if (result.toolName().equals(ArchivedToolOutputs.TOOL_NAME)) {
                    retrieved.add(result.text());
                }
                return answer("Done.");
            }
            String question = CompactionMessages.text(last);
            if (question.startsWith("Look up order ")) {
                String id = question.substring("Look up order ".length());
                return call("lookupOrder", "{\"id\":\"" + id + "\"}");
            }
            String reference = request.messages().stream()
                    .filter(m -> m instanceof ToolExecutionResultMessage)
                    .map(m -> ((ToolExecutionResultMessage) m).text())
                    .filter(text -> text.contains("reference=\""))
                    .map(text -> text.substring(text.indexOf("reference=\"") + 11, text.lastIndexOf('"')))
                    .findFirst().orElseThrow();
            return call(ArchivedToolOutputs.TOOL_NAME, "{\"reference\":\"" + reference + "\",\"offset\":0}");
        }

        private static ChatResponse call(String tool, String arguments) {
            return ChatResponse.builder().aiMessage(AiMessage.from(ToolExecutionRequest.builder()
                    .id("call_0").name(tool).arguments(arguments).build())).build();
        }

        private static ChatResponse answer(String text) {
            return ChatResponse.builder().aiMessage(AiMessage.from(text)).build();
        }
    }

    public static class FakeModelSupplier implements Supplier<ChatModel> {
        @Override
        public ChatModel get() {
            return io.quarkus.arc.Arc.container().instance(FakeModel.class).get();
        }
    }

    @Inject
    Agent agent;

    @Inject
    FakeModel model;

    @Test
    @ActivateRequestContext
    void theModelReadsAPrunedOutputAgain() {
        agent.chat("orders", "Look up order 1");
        agent.chat("orders", "Look up order 2");
        agent.chat("orders", "Look up order 3");
        agent.chat("orders", "Read the first order again");

        // the request that triggered the retrieval had the outputs of the three earlier turns pruned, with distinct
        // references although the provider sent the same id every time
        List<ChatMessage> beforeRetrieval = model.requests.get(model.requests.size() - 2);
        List<String> stubs = beforeRetrieval.stream().filter(m -> m instanceof ToolExecutionResultMessage)
                .map(m -> ((ToolExecutionResultMessage) m).text()).filter(t -> t.contains("reference=\"")).toList();
        assertThat(stubs).hasSize(3);
        assertThat(stubs.get(0)).contains("reference=\"call_0\"");
        assertThat(stubs.get(1)).contains("reference=\"call_0#");
        assertThat(stubs.get(2)).contains("reference=\"call_0#");
        assertThat(stubs).doesNotHaveDuplicates();

        assertThat(model.retrieved).containsExactly(Orders.output("1"));
    }
}
