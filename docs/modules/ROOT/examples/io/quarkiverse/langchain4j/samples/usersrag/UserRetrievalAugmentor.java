package io.quarkiverse.langchain4j.samples.usersrag;

import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.rag.AugmentationRequest;
import dev.langchain4j.rag.AugmentationResult;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.Content;
import io.quarkus.security.identity.SecurityIdentity;

/**
 * Builds the retrieval augmentor that every invocation of the AI service goes through.
 * The supplier itself is created only once, so the augmentor it returns must resolve
 * the current user on every augment call: injecting SecurityIdentity gives a client
 * proxy that resolves the identity of the current request at call time.
 */
@ApplicationScoped
public class UserRetrievalAugmentor implements Supplier<RetrievalAugmentor> {

    @Inject
    SecurityIdentity identity;

    @Inject
    UserDocuments documents;

    @Override
    public RetrievalAugmentor get() {
        return new RetrievalAugmentor() {
            @Override
            public AugmentationResult augment(AugmentationRequest request) {
                String username = identity.getPrincipal().getName();
                var contents = documents.documents(username).stream().map(Content::from).toList();
                var invocationContext = request.metadata().invocationContext();
                String prompt = "summarize".equals(invocationContext.methodName())
                        ? "Answer briefly, using only the documents below."
                        : "Answer using only the documents below.";
                StringBuilder augmented = new StringBuilder(((UserMessage) request.chatMessage()).singleText());
                augmented.append("\n\n").append(prompt).append("\n");
                for (Content content : contents) {
                    augmented.append("- ").append(content.textSegment().text()).append("\n");
                }
                return new AugmentationResult(UserMessage.from(augmented.toString()), contents);
            }
        };
    }
}
