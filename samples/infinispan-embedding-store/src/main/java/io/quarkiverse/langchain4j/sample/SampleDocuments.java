package io.quarkiverse.langchain4j.sample;

import java.util.List;

/**
 * A handful of short texts used to demonstrate semantic search.
 * They are intentionally about different but related topics so that
 * similarity search returns meaningful results.
 */
public class SampleDocuments {

    public static List<String> all() {
        return List.of(
                "Quarkus is a Kubernetes-native Java framework tailored for GraalVM and HotSpot, "
                        + "optimized for fast startup and low memory footprint.",
                "LangChain4j is a Java library for building AI applications and LLM-powered features, "
                        + "offering embeddings, chat models, and retrieval-augmented generation.",
                "Infinispan is an open-source distributed in-memory key/value data store that can also "
                        + "be used as a vector database for semantic search.",
                "Retrieval-augmented generation (RAG) combines a language model with a retrieval step "
                        + "so that answers are grounded in your own documents.",
                "Embedding models convert text into numerical vectors so that semantically similar "
                        + "sentences are close together in vector space.",
                "Quarkus LangChain4j integrates LangChain4j with Quarkus, providing extensions for chat "
                        + "models, embedding stores, and AI services.",
                "Docker is a platform for packaging applications into containers that run consistently "
                        + "across different environments.",
                "Semantic search retrieves documents based on meaning rather than exact keyword matches "
                        + "by comparing embedding vectors.");
    }
}
