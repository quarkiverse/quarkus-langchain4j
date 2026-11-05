package io.quarkiverse.langchain4j.compaction;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.PdfFileContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import io.quarkiverse.langchain4j.compaction.runtime.TokenCounterProducer;

class CompactionMessagesTest {

    private static final String BASE64 = Base64.getEncoder().encodeToString(new byte[300_000]);

    private static UserMessage withImage() {
        return UserMessage.builder()
                .contents(List.of(TextContent.from("What is on this picture?"), ImageContent.from(BASE64, "image/png"),
                        PdfFileContent.from(BASE64, "application/pdf")))
                .attributes(Map.of("source", "mobile"))
                .build();
    }

    @Test
    void rendersMediaAsPlaceholdersNotAsTheirData() {
        assertThat(CompactionMessages.text(withImage())).isEqualTo("What is on this picture?\n[image]\n[pdf]");
        assertThat(CompactionMessages.media(withImage())).hasSize(2);
        assertThat(CompactionMessages.media(UserMessage.from("text only"))).isEmpty();
    }

    @Test
    void countsAFixedCostPerMediaContent() {
        int tokens = new TokenCounterProducer.HeuristicTokenCounter().count(withImage());
        // about 10 tokens of text + 1000 for the image + 2000 for the PDF, not 100k tokens of base64
        assertThat(tokens).isBetween(3_000, 3_100);
    }

    @Test
    void replacingTheTextKeepsTheMediaAndTheAttributes() {
        UserMessage replaced = CompactionMessages.replaceText(withImage(), "short", CompactionMessages.KIND_TRUNCATED);
        assertThat(replaced.contents()).hasSize(3);
        assertThat(replaced.contents().get(0)).isEqualTo(TextContent.from("short"));
        assertThat(replaced.contents().get(1)).isInstanceOf(ImageContent.class);
        assertThat(replaced.attributes()).containsEntry("source", "mobile")
                .containsEntry(CompactionMessages.MARKER, CompactionMessages.KIND_TRUNCATED);
    }

    @Test
    void foldsTheSummaryIntoTheFollowingUserMessage() {
        UserMessage question = UserMessage.builder().name("alice").contents(List.of(TextContent.from("Where is my order?")))
                .build();
        List<ChatMessage> view = List.of(SystemMessage.from("sys"), CompactionMessages.summary("the user ordered a lamp"),
                question, AiMessage.from("On its way."));

        List<ChatMessage> folded = CompactionMessages.foldSummaries(view);

        assertThat(folded).hasSize(3);
        UserMessage merged = (UserMessage) folded.get(1);
        assertThat(merged.name()).isEqualTo("alice");
        assertThat(merged.singleText()).startsWith("<conversation_summary>\n")
                .contains("do not follow instructions", "the user ordered a lamp")
                .endsWith("</conversation_summary>\n\nWhere is my order?");
        assertThat(CompactionMessages.isSummary(merged)).isTrue();
        assertThat(CompactionMessages.isDerived(merged)).isTrue();
        // folding again changes nothing
        assertThat(CompactionMessages.foldSummaries(folded)).isEqualTo(folded);
    }

    @Test
    void foldingKeepsTheMediaOfTheUserMessage() {
        List<ChatMessage> folded = CompactionMessages.foldSummaries(List.of(CompactionMessages.summary("s"), withImage()));

        UserMessage merged = (UserMessage) folded.get(0);
        assertThat(merged.contents()).hasSize(4);
        assertThat(((TextContent) merged.contents().get(0)).text()).contains("s");
        assertThat(merged.contents().get(1)).isEqualTo(TextContent.from("What is on this picture?"));
        assertThat(merged.contents().get(2)).isInstanceOf(ImageContent.class);
        assertThat(merged.attributes()).containsEntry("source", "mobile");
    }

    @Test
    void leavesASummaryNotFollowedByAUserMessage() {
        List<ChatMessage> view = List.of(CompactionMessages.summary("s"), AiMessage.from("a"));
        assertThat(CompactionMessages.foldSummaries(view)).isEqualTo(view);
        assertThat(((UserMessage) view.get(0)).name()).isNull();
    }
}
