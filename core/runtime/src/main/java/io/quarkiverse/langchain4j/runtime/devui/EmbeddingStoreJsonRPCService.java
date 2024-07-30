package io.quarkiverse.langchain4j.runtime.devui;

import java.util.regex.Pattern;

import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.enterprise.util.TypeLiteral;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class EmbeddingStoreJsonRPCService {

    EmbeddingStore<TextSegment> embeddingStore;

    EmbeddingModel embeddingModel;

    public EmbeddingStoreJsonRPCService() {
        // if there are more models/stores, try to choose the default
        embeddingModel = CDI.current().select(EmbeddingModel.class, new Default.Literal()).get();
        TypeLiteral<EmbeddingStore<TextSegment>> embeddingStoreType = new TypeLiteral<EmbeddingStore<TextSegment>>() {
        };
        embeddingStore = CDI.current().select(embeddingStoreType, new Default.Literal()).get();
    }

    private static final Pattern COMMA_OR_NEWLINE = Pattern.compile(",|\\r?\\n");

    public String add(String id, String text, String metadata) {
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
}
