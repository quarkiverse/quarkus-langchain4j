package org.acme.example;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import jakarta.inject.Singleton;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.injector.ContentInjector;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.rag.query.router.QueryRouter;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService(retrievalAugmentor = AiServiceWithQueryRouterAndContentInjector.QueryRouterAugmentor.class)
public interface AiServiceWithQueryRouterAndContentInjector {

    String chat(String message);

    /**
     * Contains a query transformer that transforms the query text to
     * lowercase. If the transformed worked properly, the content retriever
     * will append content saying "The transformer works!".
     */
    @Singleton
    class QueryRouterAugmentor implements Supplier<RetrievalAugmentor> {

        @Override
        public RetrievalAugmentor get() {
            return DefaultRetrievalAugmentor.builder()
                    .queryRouter(new QueryRouter() {
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
                    })
                    .contentInjector(new ContentInjector() {
                        @Override
                        public ChatMessage inject(List<Content> contents, ChatMessage chatMessage) {
                            String rewrittenMessage = ((UserMessage) chatMessage).singleText() + " - "
                                    + contents.get(0).textSegment().text();
                            return UserMessage.userMessage(rewrittenMessage);
                        }
                    })
                    .build();
        }

        static ContentRetriever dogsRetriever() {
            return new ContentRetriever() {
                @Override
                public List<Content> retrieve(Query query) {
                    return Collections.singletonList(Content.from("Dogs bark"));
                }
            };
        }

        static ContentRetriever catsRetriever() {
            return new ContentRetriever() {
                @Override
                public List<Content> retrieve(Query query) {
                    return Collections.singletonList(Content.from("Cats meow"));
                }
            };
        }
    }
}
