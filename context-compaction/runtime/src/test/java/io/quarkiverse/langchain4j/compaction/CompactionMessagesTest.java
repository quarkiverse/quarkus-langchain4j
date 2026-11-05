package io.quarkiverse.langchain4j.compaction;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.PdfFileContent;
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
}
