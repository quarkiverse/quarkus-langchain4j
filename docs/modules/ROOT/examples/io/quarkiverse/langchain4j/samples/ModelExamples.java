package io.quarkiverse.langchain4j.samples;

import java.util.Map;

import jakarta.inject.Inject;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.image.ImageModel;
import dev.langchain4j.model.moderation.Moderation;
import dev.langchain4j.model.moderation.ModerationModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.service.Moderate;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.smallrye.mutiny.Multi;

public class ModelExamples {

    // tag::ai-service[]
    @RegisterAiService // Use the default ChatModel
    public interface MyAiService {

        @UserMessage("What is the capital of France?")
        String askCapitalOfFrance();

        @UserMessage("Translate 'Hello' to French")
        String translateHelloToFrench();
    }
    // end::ai-service[]

    // tag::programmatic-chat-model-1[]
    @Inject
    ChatModel chatModel;
    // end::programmatic-chat-model-1[]

    public void exampleProgrammaticChatModel() {
        // tag::programmatic-chat-model-2[]
        // ...
        String response = chatModel.chat("Summarize this document...");
        // end::programmatic-chat-model-2[]
    }

    // tag::streaming-ai-service[]
    @RegisterAiService // Use the default StreamingChatModel
    public interface MyStreamingAiService {

        @UserMessage("What is the capital of France?")
        Multi<String> askCapitalOfFrance();
    }
    // end::streaming-ai-service[]

    // tag::programmatic-chat-streaming-model-1[]
    @Inject
    StreamingChatModel streamingChatModel;
    // end::programmatic-chat-streaming-model-1[]

    public void exampleProgrammaticChatStreamingModel() {

        // tag::programmatic-chat-streaming-model-2[]
        // ...
        streamingChatModel
                .chat("Explain Quarkus", new StreamingChatResponseHandler() {
                    @Override
                    public void onPartialResponse(String token) {
                        System.out.println("Received token: " + token);
                    }

                    @Override
                    public void onCompleteResponse(ChatResponse completeResponse) {
                        System.out.println("Completed streaming.");
                    }

                    @Override
                    public void onError(Throwable error) {
                        error.printStackTrace();
                    }
                });
        // end::programmatic-chat-streaming-model-2[]
    }

    // tag::embedding-model-1[]
    @Inject
    EmbeddingModel embeddingModel;
    // end::embedding-model-1[]

    public void exampleEmbeddingModel() {
        // tag::embedding-model-2[]
        //...
        // Use the injected embedding model to embed a text
        TextSegment segment = TextSegment.textSegment("Hello, Quarkus!",
                Metadata.metadata("file", "example.txt"));
        Response<Embedding> response = embeddingModel.embed(segment);
        float[] vector = response.content().vector();
        Map<String, Object> metadata = response.metadata();
        // Example usage of the embedding
        System.out.println("Embedding vector: " + java.util.Arrays.toString(vector));
        System.out.println("Embedding metadata: " + metadata);
        // end::embedding-model-2[]

    }

    // tag::moderation[]
    @RegisterAiService // Use the default ModerationService
    public interface MyAiServiceWithModeration {

        @Moderate
        @UserMessage("Please answer that question: {input}")
        String answer(String input);
    }
    // end::moderation[]

    // tag::programmatic-moderation-service-1[]
    @Inject
    ModerationModel moderationModel;
    // end::programmatic-moderation-service-1[]

    public void exampleModeration() {
        // tag::programmatic-moderation-service-2[]
        // ...
        Moderation moderation = moderationModel.moderate("What's the meaning of life?")
                .content();
        if (moderation.flagged()) {
            System.out.println("The message was flagged for moderation.");
            System.out.println("Flagged text: " + moderation.flaggedText());
        } else {
            System.out.println("The message is safe.");
        }
        // end::programmatic-moderation-service-2[]
    }

    // tag::image-service[]
    @RegisterAiService
    public interface MyImageService {

        @UserMessage("Generate an image of a sunset over the mountains")
        Image generateSunsetImage();

        @UserMessage("Describe this image: {image}")
        String describeImage(Image image);
    }
    // end::image-service[]

    // tag::programmatic-image-model-1[]
    @Inject
    ImageModel imageModel;
    // end::programmatic-image-model-1[]

    public void exampleImageModel() {
        // tag::programmatic-image-model-2[]
        // ...
        Response<Image> response = imageModel.generate("A futuristic cityscape at sunset");
        System.out.println("Generated image URL: " + response.content().url());
        response = imageModel.edit(response.content(), "with a flying car in the sky");
        // end::programmatic-image-model-2[]
    }
}
