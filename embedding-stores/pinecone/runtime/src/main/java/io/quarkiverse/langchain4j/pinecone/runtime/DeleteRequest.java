package io.quarkiverse.langchain4j.pinecone.runtime;

import java.util.List;
import java.util.Map;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Represents a Delete vector operation against Pinecone.
 * See the <a href="https://docs.pinecone.io/reference/delete_post">API documentation</a>.
 */
@RegisterForReflection
public class DeleteRequest {

    private final List<String> ids;

    private final Boolean deleteAll;

    private final String namespace;

    private final Map<String, String> filter;

    public DeleteRequest(List<String> ids, Boolean deleteAll, String namespace, Map<String, String> filter) {
        this.ids = ids;
        this.deleteAll = deleteAll;
        this.namespace = namespace;
        this.filter = filter;
    }

    public List<String> getIds() {
        return ids;
    }

    public boolean isDeleteAll() {
        return deleteAll;
    }

    public String getNamespace() {
        return namespace;
    }

    public Map<String, String> getFilter() {
        return filter;
    }
}
