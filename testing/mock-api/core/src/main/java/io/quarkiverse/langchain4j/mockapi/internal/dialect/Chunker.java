package io.quarkiverse.langchain4j.mockapi.internal.dialect;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.quarkiverse.langchain4j.mockapi.ChatReply;

/**
 * Splits a reply into the pieces a streaming dialect sends as separate deltas.
 */
public final class Chunker {

    private static final Pattern WORD = Pattern.compile("\\s*\\S+");

    private Chunker() {
    }

    /**
     * @return the explicit {@code reply.chunks()} when set, otherwise the text split on word boundaries with the
     *         whitespace attached to the start of the following word ({@code "As an AI"} gives {@code "As", " an", " AI"});
     *         empty text gives an empty list
     */
    public static List<String> chunks(ChatReply reply) {
        if (reply.chunks() != null) {
            return reply.chunks();
        }
        List<String> result = new ArrayList<>();
        String text = reply.text();
        if (text == null || text.isEmpty()) {
            return result;
        }
        Matcher matcher = WORD.matcher(text);
        int end = 0;
        while (matcher.find()) {
            result.add(matcher.group());
            end = matcher.end();
        }
        if (result.isEmpty()) {
            result.add(text);
        } else if (end < text.length()) {
            int last = result.size() - 1;
            result.set(last, result.get(last) + text.substring(end));
        }
        return result;
    }
}
