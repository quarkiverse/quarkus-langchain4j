package io.quarkiverse.langchain4j.runtime.devui.json;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ChatMessagePojoTest {

    @Test
    void appendedRagContextIsWrappedInContextBox() {
        String original = "What's the return policy?";
        String augmented = original + "\n\nAnswer using the following information:\nItems can be returned within 30 days.";

        String result = ChatMessagePojo.formatRagAugmentedText(original, augmented);

        assertEquals(original + "\n\n<blockquote class=\"lc4j-rag-context\"><strong>Retrieved context</strong><br>"
                + "Answer using the following information:<br>"
                + "Items can be returned within 30 days.</blockquote>", result);
    }

    @Test
    void insertedRagContextInTheMiddleIsWrappedInContextBox() {
        String original = "hey there";
        String augmented = original.substring(0, 4) + "\n\nItems can be returned within 30 days.\n\n"
                + original.substring(4);

        String result = ChatMessagePojo.formatRagAugmentedText(original, augmented);

        assertEquals("hey\n\n<blockquote class=\"lc4j-rag-context\"><strong>Retrieved context</strong><br>"
                + "Items can be returned within 30 days.</blockquote>\n\nthere", result);
    }

    @Test
    void htmlSpecialCharactersInRagContextAreEscaped() {
        String original = "question";
        String augmented = original + "\n\nuse <tag> & \"quotes\"";

        String result = ChatMessagePojo.formatRagAugmentedText(original, augmented);

        assertEquals(original + "\n\n<blockquote class=\"lc4j-rag-context\"><strong>Retrieved context</strong><br>"
                + "use &lt;tag&gt; &amp; &quot;quotes&quot;</blockquote>", result);
    }

    @Test
    void partiallyRewrittenMessageIsReturnedUnchanged() {
        String original = "the cat sat";
        String augmented = "the dog sat";

        String result = ChatMessagePojo.formatRagAugmentedText(original, augmented);

        assertEquals(augmented, result);
    }

    @Test
    void rewrittenMessageIsReturnedUnchanged() {
        String original = "What's the return policy?";
        String augmented = "Completely different text produced by a custom RetrievalAugmentor.";

        String result = ChatMessagePojo.formatRagAugmentedText(original, augmented);

        assertEquals(augmented, result);
    }

    @Test
    void unmodifiedMessageIsReturnedUnchanged() {
        String original = "What's the return policy?";

        String result = ChatMessagePojo.formatRagAugmentedText(original, original);

        assertEquals(original, result);
    }
}
