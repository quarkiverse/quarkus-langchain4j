package io.quarkiverse.langchain4j.runtime.devui;

import java.util.List;
import java.util.regex.Pattern;

import jakarta.enterprise.inject.Default;

import org.jboss.logging.Logger;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.quarkus.arc.All;
import io.quarkus.arc.Arc;
import io.quarkus.arc.InstanceHandle;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class EmbeddingStoreJsonRPCService {

    final EmbeddingStore<TextSegment> embeddingStore;

    final EmbeddingModel embeddingModel;

    private static Logger log = Logger.getLogger(EmbeddingStoreJsonRPCService.class);

    public EmbeddingStoreJsonRPCService(
            @All List<EmbeddingStore<TextSegment>> embeddingStores) {
        List<InstanceHandle<EmbeddingModel>> embeddingModels = Arc.container().listAll(EmbeddingModel.class);
        if (embeddingModels.isEmpty()) {
            embeddingModel = null;
            log.warn("EmbeddingStoreJsonRPCService is unable to find any embedding model in CDI, " +
                    "the embedding store Dev UI page will not work");
        } else {
            if (embeddingModels.size() > 1) {
                // if there's more than one, try to find the one with @Default
                Default.Literal DEFAULT = new Default.Literal();
                EmbeddingModel chosenModel = null;
                for (InstanceHandle<EmbeddingModel> candidate : embeddingModels) {
                    if (candidate.getBean().getQualifiers().contains(DEFAULT)) {
                        chosenModel = candidate.get();
                        break;
                    }
                }
                if (chosenModel == null) {
                    // didn't find a @Default one
                    embeddingModel = embeddingModels.get(0).get();
                } else {
                    embeddingModel = chosenModel;
                }
                log.warn("EmbeddingStoreJsonRPCService found multiple embedding models in CDI, " +
                        "using the first one: " + embeddingModel.getClass().getName());
            } else {
                embeddingModel = embeddingModels.get(0).get();
            }
        }
        if (embeddingStores.isEmpty()) {
            embeddingStore = null;
            log.warn("EmbeddingStoreJsonRPCService is unable to find any embedding store in CDI, " +
                    "the embedding store Dev UI page will not work");
        } else {
            embeddingStore = embeddingStores.get(0);
            if (embeddingStores.size() > 1) {
                log.warn("EmbeddingStoreJsonRPCService found multiple embedding stores in CDI, " +
                        "using the first one: " + embeddingStore.getClass().getName());
            }
        }
    }

    private static final Pattern COMMA_OR_NEWLINE = Pattern.compile(",|\\r?\\n");

    public String add(String id, String text, String metadata) {
        verifyEmbeddingModelAndStore();
        if (id == null || id.isEmpty()) {
            return embeddingStore.add(embeddingModel.embed(text).content(), TextSegment.from(text, parseMetadata(metadata)));
        } else {
            embeddingStore.add(id, embeddingModel.embed(TextSegment.from(text, parseMetadata(metadata))).content());
            return id;
        }
    }

    private Metadata parseMetadata(String metadata) {
        Metadata metadataObject = new Metadata();
        for (String metadataField : COMMA_OR_NEWLINE.split(metadata)) {
            // FIXME: this doesn't allow any kind of escaping the `=` or `,` characters; do we need that?
            String[] keyValue = metadataField.split("=");
            if (keyValue.length == 2) {
                metadataObject.add(keyValue[0].trim(), keyValue[1].trim());
            }
        }
        return metadataObject;
    }

    // FIXME: the limit argument can be changed to int after https://github.com/quarkusio/quarkus/issues/37481 is fixed
    // LangChain4jDevUIJsonRpcTest will need to be adjusted accordingly
    public JsonArray findRelevant(String text, String limit) {
        verifyEmbeddingModelAndStore();
        int limitInt = Integer.parseInt(limit);
        JsonArray result = new JsonArray();
        for (EmbeddingMatch<TextSegment> match : embeddingStore.findRelevant(embeddingModel.embed(text).content(), limitInt)) {
            JsonObject matchJson = new JsonObject();
            matchJson.put("embeddingId", match.embeddingId());
            matchJson.put("score", match.score());
            matchJson.put("embedded", match.embedded() != null ? match.embedded().text() : null);
            JsonArray metadata = new JsonArray();
            if (match.embedded() != null && match.embedded().metadata() != null) {
                for (String key : match.embedded().metadata().asMap().keySet()) {
                    JsonObject metadataEntry = new JsonObject();
                    metadataEntry.put("key", key);
                    metadataEntry.put("value", match.embedded().metadata().get(key));
                    metadata.add(metadataEntry);
                }
            }
            matchJson.put("metadata", metadata);
            result.add(matchJson);
        }
        return result;
    }

    private void verifyEmbeddingModelAndStore() {
        if (embeddingModel == null) {
            throw new RuntimeException("No embedding model found in CDI. Please add an embedding model to your application.");
        }
        if (embeddingStore == null) {
            throw new RuntimeException("No embedding store found in CDI. Please add an embedding store to your application.");
        }
    }
}
