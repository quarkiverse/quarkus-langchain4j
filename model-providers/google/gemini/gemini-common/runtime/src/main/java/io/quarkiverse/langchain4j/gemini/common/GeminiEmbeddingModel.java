package io.quarkiverse.langchain4j.gemini.common;

import java.util.List;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;

public abstract class GeminiEmbeddingModel implements EmbeddingModel {

    private final String modelId;
    private final Integer dimension;
    private final String taskType;

    public GeminiEmbeddingModel(String modelId, Integer dimension, String taskType) {
        this.modelId = modelId;
        this.dimension = dimension;
        this.taskType = taskType;
    }

    @Override
    public Response<Embedding> embed(String text) {

        EmbedContentRequest embedContentRequest = getEmbedContentRequest(modelId, text);

        EmbedContentResponse embedContentResponse = embedContent(embedContentRequest);

        return Response.from(Embedding.from(embedContentResponse.embedding().values()));
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        List<EmbedContentRequest> embedContentRequests = textSegments.stream()
                .map(textSegment -> getEmbedContentRequest(modelId, textSegment.text()))
                .toList();

        EmbedContentResponses embedContentResponses = batchEmbedContents(
                new EmbedContentRequests(embedContentRequests));

        List<Embedding> embeddings = embedContentResponses.embeddings()
                .stream()
                .map(embedding -> Embedding.from(embedding.values()))
                .toList();
        return Response.from(embeddings);
    }

    private EmbedContentRequest getEmbedContentRequest(String model, String text) {
        Content.Part part = Content.Part.ofText(text);
        Content content = Content.ofPart(part);

        EmbedContentRequest.TaskType embedTaskType = null;
        if (this.taskType != null) {
            embedTaskType = EmbedContentRequest.TaskType.valueOf(this.taskType);
        }

        EmbedContentRequest embedContentRequest = new EmbedContentRequest("models/" + model, content,
                embedTaskType, null, this.dimension);
        return embedContentRequest;
    }

    protected abstract EmbedContentResponse embedContent(EmbedContentRequest embedContentRequest);

    protected abstract EmbedContentResponses batchEmbedContents(EmbedContentRequests embedContentRequests);

}
