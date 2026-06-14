package org.acme.example;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import jakarta.inject.Singleton;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.injector.ContentInjector;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.rag.query.router.QueryRouter;
import io.quarkiverse.langchain4j.RagPipeline;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService
@RagPipeline(router = AiServiceWithQueryRouterAndContentInjector.DogCatRouter.class, injector = AiServiceWithQueryRouterAndContentInjector.PrependingInjector.class)
public interface AiServiceWithQueryRouterAndContentInjector {

    String chat(String message);

    @Singleton
    class DogCatRouter implements QueryRouter {
        @Override
        public Collection<ContentRetriever> route(Query query) {
            if (query.text().contains("dog")) {
                return Collections.singletonList(dogsRetriever());
            } else if (query.text().contains("cat")) {
                return Collections.singletonList(catsRetriever());
            } else {
                return Collections.emptyList();
            }
        }

        static ContentRetriever dogsRetriever() {
            return query -> Collections.singletonList(Content.from("Dogs bark"));
        }

        static ContentRetriever catsRetriever() {
            return query -> Collections.singletonList(Content.from("Cats meow"));
        }
    }

    @Singleton
    class PrependingInjector implements ContentInjector {
        @Override
        public ChatMessage inject(List<Content> contents, ChatMessage chatMessage) {
            String rewrittenMessage = ((UserMessage) chatMessage).singleText() + " - "
                    + contents.get(0).textSegment().text();
            return UserMessage.userMessage(rewrittenMessage);
        }
    }
}
