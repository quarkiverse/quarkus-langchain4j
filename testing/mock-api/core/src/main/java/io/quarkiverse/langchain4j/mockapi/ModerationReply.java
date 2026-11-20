package io.quarkiverse.langchain4j.mockapi;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import io.quarkiverse.langchain4j.mockapi.internal.model.ModerationReplyImpl;

public non-sealed interface ModerationReply extends Reply {

    static ModerationReply flagged(String category, String... moreCategories) {
        List<String> categories = new ArrayList<>();
        categories.add(category);
        categories.addAll(List.of(moreCategories));
        return new ModerationReplyImpl(true, List.copyOf(categories), null);
    }

    static ModerationReply notFlagged() {
        return new ModerationReplyImpl(false, List.of(), null);
    }

    boolean flagged();

    List<String> categories();

    ModerationReply delay(Duration delay);
}
