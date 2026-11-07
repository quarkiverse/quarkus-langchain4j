package io.quarkiverse.langchain4j.compaction.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.logging.Level;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * {@code retrieval=tool} without {@code ArchivedToolOutputs} in the tools of the AI services: the stubs would tell the
 * model to call a tool it does not have, so the extension warns at startup.
 */
class MissingRetrievalToolTest {

    @RegisterExtension
    static final QuarkusExtensionTest test = new QuarkusExtensionTest()
            .withApplicationRoot(jar -> jar.addClasses(Assistant.class, CompactionProfilesTest.FakeModel.class,
                    CompactionProfilesTest.FakeModelSupplier.class))
            .overrideConfigKey("quarkus.langchain4j.compaction.strategy", "tool-output-pruning")
            .overrideConfigKey("quarkus.langchain4j.compaction.tool-output-pruning.retrieval", "tool")
            .setLogRecordPredicate(r -> r.getLevel().intValue() >= Level.WARNING.intValue())
            .assertLogRecords(records -> assertThat(records).anyMatch(r -> r.getMessage()
                    .contains("sets tool-output-pruning.retrieval=tool, but none of its AI services")));

    @RegisterAiService(chatLanguageModelSupplier = CompactionProfilesTest.FakeModelSupplier.class)
    interface Assistant {
        String chat(@MemoryId String id, @UserMessage String question);
    }

    @Test
    void warns() {
        // see assertLogRecords
    }
}
